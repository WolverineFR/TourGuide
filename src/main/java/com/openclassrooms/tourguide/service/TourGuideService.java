package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearbyAttractionDTO;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

/**
 * TourGuideService
 *
 * Service central de l'application TourGuide. Il orchestre : La récupération et
 * le suivi de la localisation des utilisateurs Le calcul et la récupération des
 * récompenses La récupération des attractions proches La génération d'offres de
 * voyage personnalisées (trip deals)
 * 
 * Ce service utilise {@link GpsUtil} pour la géolocalisation,
 * {@link RewardsService} pour les récompenses et {@link TripPricer} pour les
 * offres de voyage. Il maintient également une base d'utilisateurs internes
 * pour les tests.
 */
@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;
	private final ExecutorService executor;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		this.executor = Executors.newFixedThreadPool(100);

		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	/**
	 * Récupère la liste des récompenses associées à un utilisateur.
	 *
	 * @param user l'utilisateur concerné
	 * @return liste des {@link UserReward} obtenues par l'utilisateur
	 */
	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	/**
	 * Récupère la dernière localisation connue d'un utilisateur, ou déclenche un
	 * suivi pour en obtenir une si aucune n'existe.
	 *
	 * @param user l'utilisateur concerné
	 * @return la localisation {@link VisitedLocation} la plus récente
	 */
	public VisitedLocation getUserLocation(User user) {
		if (user.getVisitedLocations().size() > 0) {
			return user.getLastVisitedLocation();
		} else {
			return trackUserLocation(user).join();
		}
	}

	/**
	 * Récupère un utilisateur à partir de son nom.
	 *
	 * @param userName nom de l'utilisateur
	 * @return l'objet {@link User} correspondant, ou {@code null} s'il n'existe pas
	 */
	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	/**
	 * Récupère tous les utilisateurs enregistrés (internes).
	 *
	 * @return liste des utilisateurs
	 */
	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	/**
	 * Ajoute un utilisateur dans la base interne s'il n'existe pas déjà.
	 *
	 * @param user utilisateur à ajouter
	 */
	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	/**
	 * Génère des offres de voyage personnalisées pour un utilisateur en fonction de
	 * ses préférences et de ses points de récompense cumulés.
	 *
	 * @param user l'utilisateur concerné
	 * @return liste des offres {@link Provider}
	 */
	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	/**
	 * Suit et enregistre la localisation d'un utilisateur de manière asynchrone,
	 * puis déclenche le calcul des récompenses.
	 *
	 * @param user l'utilisateur à suivre
	 * @return un {@link CompletableFuture} contenant la {@link VisitedLocation}
	 *         obtenue
	 */
	public CompletableFuture<VisitedLocation> trackUserLocation(User user) {
		return CompletableFuture.supplyAsync(() -> {
			VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
			user.addToVisitedLocations(visitedLocation);
			return visitedLocation;
		}, executor)
				.thenCompose(visitedLocation -> rewardsService.calculateRewards(user).thenApply(vl -> visitedLocation));
	}

	/**
	 * Récupère les cinq attractions les plus proches de la localisation d'un
	 * utilisateur, triées par distance croissante.
	 *
	 * @param visitedLocation localisation actuelle de l'utilisateur
	 * @param user            l'utilisateur concerné
	 * @return liste des {@link NearbyAttractionDTO} (nom, coordonnées, distance et
	 *         points de récompense)
	 */
	public List<NearbyAttractionDTO> getNearByAttractions(VisitedLocation visitedLocation, User user) {
		List<NearbyAttractionDTO> nearbyAttractions = new ArrayList<>();
		List<Attraction> attractionsSorted = gpsUtil.getAttractions().stream()
				.sorted(Comparator
						.comparing(attraction -> rewardsService.getDistance(visitedLocation.location, attraction)))
				.limit(5).toList();

		for (Attraction attraction : attractionsSorted) {
			double distance = rewardsService.getDistance(visitedLocation.location, attraction);
			int rewardPoints = rewardsService.getRewardPoints(attraction, user);

			NearbyAttractionDTO attractionDto = new NearbyAttractionDTO(attraction.attractionName, attraction.latitude,
					attraction.longitude, visitedLocation.location.latitude, visitedLocation.location.longitude,
					distance, rewardPoints);
			nearbyAttractions.add(attractionDto);
		}
		return nearbyAttractions;
	}

	/**
	 * Ajoute un hook de fermeture de l'application pour arrêter proprement le
	 * tracker.
	 */
	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	/**
	 * Initialise les utilisateurs internes pour les tests.
	 */
	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}

}

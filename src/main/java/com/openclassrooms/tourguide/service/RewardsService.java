package com.openclassrooms.tourguide.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

/**
 * RewardsService
 * 
 * Service chargé de la gestion des récompenses des utilisateurs dans
 * l'application TourGuide. Il permet de :
 * 
 * Calculer les récompenses en fonction des attractions visitées et de la
 * proximité. Déterminer si une localisation se trouve à proximité d'une
 * attraction. Calculer les points de récompense associés à une attraction et un
 * utilisateur donné. Mesurer la distance entre deux coordonnées géographiques.
 *
 * Les calculs intensifs sont effectués de manière asynchrone à l'aide d'un
 * {@link ExecutorService}.
 **/
@Service
public class RewardsService {
	private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
	private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private final ExecutorService executor;

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
		this.executor = Executors.newFixedThreadPool(100);
	}

	/**
	 * Définit une nouvelle distance (en miles) utilisée pour déterminer la
	 * proximité d'une attraction.
	 *
	 * @param proximityBuffer distance personnalisée en miles
	 */
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	/**
	 * Réinitialise la distance de proximité à la valeur par défaut.
	 */
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * Calcule les récompenses d'un utilisateur de manière asynchrone.
	 *
	 * Pour chaque position visitée par l'utilisateur, on vérifie sa proximité avec
	 * toutes les attractions. Si une attraction est proche et qu'elle n'a pas déjà
	 * donné lieu à une récompense, une nouvelle {@link UserReward} est ajoutée.
	 *
	 * @param user l'utilisateur pour lequel calculer les récompenses
	 * @return un {@link CompletableFuture} représentant l'exécution asynchrone du
	 *         calcul
	 */
	public CompletableFuture<Void> calculateRewards(User user) {
		return CompletableFuture.runAsync(() -> {

			List<VisitedLocation> userLocations = user.getVisitedLocations();
			List<Attraction> attractions = gpsUtil.getAttractions();
			List<VisitedLocation> userLocationsCopy = new ArrayList<>(userLocations);

			for (VisitedLocation visitedLocation : userLocationsCopy) {
				for (Attraction attraction : attractions) {
					if (user.getUserRewards().stream()
							.filter(r -> r.attraction.attractionName.equals(attraction.attractionName)).count() == 0) {
						if (nearAttraction(visitedLocation, attraction)) {
							user.addUserReward(
									new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
						}
					}
				}
			}
		}, executor);
	};

	/**
	 * Vérifie si une localisation se trouve dans le rayon de proximité d'une
	 * attraction.
	 *
	 * @param attraction l'attraction à vérifier
	 * @param location   la localisation à comparer
	 * @return {@code true} si la localisation est dans la zone de l'attraction,
	 *         {@code false} sinon
	 */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}

	/**
	 * Vérifie si une localisation visitée est suffisamment proche d'une attraction,
	 * en utilisant le buffer de proximité configuré.
	 *
	 * @param visitedLocation la localisation visitée par l'utilisateur
	 * @param attraction      l'attraction à comparer
	 * @return {@code true} si la distance est inférieure ou égale au buffer de
	 *         proximité, {@code false} sinon
	 */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}

	/**
	 * Récupère le nombre de points de récompense pour une attraction donnée et un
	 * utilisateur donné.
	 *
	 * @param attraction l'attraction pour laquelle obtenir les points
	 * @param user       l'utilisateur concerné
	 * @return le nombre de points de récompense
	 */
	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	/**
	 * Calcule la distance en miles entre deux coordonnées géographiques.
	 *
	 * @param loc1 première localisation
	 * @param loc2 seconde localisation
	 * @return distance en miles
	 */
	public double getDistance(Location loc1, Location loc2) {
		double lat1 = Math.toRadians(loc1.latitude);
		double lon1 = Math.toRadians(loc1.longitude);
		double lat2 = Math.toRadians(loc2.latitude);
		double lon2 = Math.toRadians(loc2.longitude);

		double angle = Math
				.acos(Math.sin(lat1) * Math.sin(lat2) + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

		double nauticalMiles = 60 * Math.toDegrees(angle);
		double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
		return statuteMiles;
	}

}

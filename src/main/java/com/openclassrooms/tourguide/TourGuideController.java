package com.openclassrooms.tourguide;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.dto.NearbyAttractionDTO;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import tripPricer.Provider;

/**
 * Contrôleur REST principal de l'application TourGuide.
 */

@RestController
public class TourGuideController {

	@Autowired
	TourGuideService tourGuideService;

	/**
	 * Endpoint racine de l'API.
	 *
	 * @return un message de bienvenue simple confirmant le bon fonctionnement du
	 *         service.
	 */
	@RequestMapping("/")
	public String index() {
		return "Greetings from TourGuide!";
	}

	/**
	 * Récupère la position actuelle de l'utilisateur spécifié.
	 *
	 * @param userName le nom de l'utilisateur (obligatoire)
	 * @return un objet {@link VisitedLocation} représentant la localisation
	 *         actuelle de l'utilisateur.
	 */
	@RequestMapping("/getLocation")
	public VisitedLocation getLocation(@RequestParam String userName) {
		return tourGuideService.getUserLocation(getUser(userName));
	}

	/**
	 * Récupère la liste des attractions proches de la position actuelle de
	 * l'utilisateur.
	 *
	 * @param userName le nom de l'utilisateur (obligatoire)
	 * @return une liste de {@link NearbyAttractionDTO} contenant les informations
	 *         des attractions les plus proches.
	 */
	@RequestMapping("/getNearbyAttractions")
	public List<NearbyAttractionDTO> getNearbyAttractions(@RequestParam String userName) {
		VisitedLocation visitedLocation = tourGuideService.getUserLocation(getUser(userName));
		return tourGuideService.getNearByAttractions(visitedLocation, getUser(userName));
	}

	/**
	 * Récupère la liste des récompenses associées à l'utilisateur.
	 *
	 * @param userName le nom de l'utilisateur (obligatoire)
	 * @return une liste de {@link UserReward} correspondant aux récompenses
	 *         obtenues.
	 */
	@RequestMapping("/getRewards")
	public List<UserReward> getRewards(@RequestParam String userName) {
		return tourGuideService.getUserRewards(getUser(userName));
	}

	/**
	 * Récupère les offres de voyage (trip deals) personnalisées pour l'utilisateur.
	 *
	 * @param userName le nom de l'utilisateur (obligatoire)
	 * @return une liste de {@link Provider} représentant les offres proposées.
	 */
	@RequestMapping("/getTripDeals")
	public List<Provider> getTripDeals(@RequestParam String userName) {
		return tourGuideService.getTripDeals(getUser(userName));
	}

	/**
	 * Méthode utilitaire interne pour récupérer l'objet {@link User} à partir de
	 * son nom.
	 *
	 * @param userName le nom de l'utilisateur
	 * @return l'objet {@link User} correspondant.
	 */
	private User getUser(String userName) {
		return tourGuideService.getUser(userName);
	}

}
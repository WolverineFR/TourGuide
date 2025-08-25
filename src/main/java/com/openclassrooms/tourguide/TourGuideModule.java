package com.openclassrooms.tourguide;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import gpsUtil.GpsUtil;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.service.RewardsService;

/**
 * Classe de configuration Spring qui définit les beans nécessaires au
 * fonctionnement de l'application TourGuide.
 */

@Configuration
public class TourGuideModule {

	/**
	 * Fournit une instance de {@link GpsUtil}.
	 *
	 * @return un objet {@link GpsUtil} permettant d'accéder aux données de
	 *         localisation.
	 */
	@Bean
	public GpsUtil getGpsUtil() {
		return new GpsUtil();
	}

	/**
	 * Fournit une instance de {@link RewardsService}.
	 *
	 * Le service utilise {@link GpsUtil} et {@link RewardCentral} pour calculer et
	 * gérer les récompenses des utilisateurs.
	 *
	 * @return un objet {@link RewardsService} prêt à l'emploi.
	 */
	@Bean
	public RewardsService getRewardsService() {
		return new RewardsService(getGpsUtil(), getRewardCentral());
	}

	/**
	 * Fournit une instance de {@link RewardCentral}.
	 *
	 * @return un objet {@link RewardCentral} pour interagir avec le système
	 *         centralisé des récompenses.
	 */
	@Bean
	public RewardCentral getRewardCentral() {
		return new RewardCentral();
	}

}

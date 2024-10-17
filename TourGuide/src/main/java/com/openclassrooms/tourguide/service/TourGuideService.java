package com.openclassrooms.tourguide.service;

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
import java.util.concurrent.ExecutionException;
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

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

	// Adding a thread pool to handle multithreading
	private final ExecutorService executorService = Executors
			.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;

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

	// Method to track a user's location asynchronously
	public CompletableFuture<Void> trackUserLocationAsync(User user) {
		return CompletableFuture.runAsync(() -> trackUserLocation(user), executorService);
	}

	// Method to track the location of all users in parallel
	public void trackAllUsersLocation(List<User> users) throws InterruptedException, ExecutionException {
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (User user : users) {
			futures.add(trackUserLocationAsync(user)); // Run tracking for each user in parallel
		}

		// Wait for all tasks to complete
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
	}

	// Ensures the correct closure of thread pools
	public void shutdownService() {
		executorService.shutdown();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		return visitedLocation;
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}

	/*
	 * Modified code to return the 5 closest attractions instead of returning a list
	 * of attractions
	 */
	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> attractions = gpsUtil.getAttractions();

		// Sorting attractions by distance from the user's position
		List<Attraction> sortedAttractions = attractions.stream()
				.sorted(Comparator.comparingDouble(
						attraction -> rewardsService.getDistance(attraction, visitedLocation.location)))
				.limit(5) // Limit to 5 attractions
				.collect(Collectors.toList());
		return sortedAttractions;
	}

	/*
	 * Added method to display new detailed JSON object, using getNearByAttractions
	 * as a starting point to extract attractions
	 */
	public List<Map<String, Object>> getNearbyAttractionsWithDetails(VisitedLocation visitedLocation, User user) {
		List<Attraction> attractions = getNearByAttractions(visitedLocation);

		return attractions.stream().map(attraction -> {
			double distanceInMiles = rewardsService.getDistance(attraction, visitedLocation.location);

			int rewardPoints = rewardsService.getRewardPoints(attraction, user);

			Map<String, Object> attractionInfo = new HashMap<>();
			attractionInfo.put("Attraction name: ", attraction.attractionName);
			attractionInfo.put("Attraction latitude: ", attraction.latitude);
			attractionInfo.put("Attraction longitude: ", attraction.longitude);
			attractionInfo.put("User's latitude: ", visitedLocation.location.latitude);
			attractionInfo.put("user's longitude: ", visitedLocation.location.longitude);
			attractionInfo.put("Distance in miles: ", distanceInMiles);
			attractionInfo.put("Reward points: ", rewardPoints);

			return attractionInfo;
		}).collect(Collectors.toList());
	}

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

package com.openclassrooms.tourguide;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import tripPricer.Provider;

@RestController
public class TourGuideController {

	@Autowired
	TourGuideService tourGuideService;

	@Autowired
	RewardsService rewardsService;

	@GetMapping("/")
	public String index() {
		return "Greetings from TourGuide!";
	}

	// Added to get users to test on Postman
	@GetMapping("/users")
	public List<User> getAllUsers() {
		return tourGuideService.getAllUsers();
	}

	@RequestMapping("/getLocation")
	public VisitedLocation getLocation(@RequestParam String userName) {
		return tourGuideService.getUserLocation(getUser(userName));
	}

	@RequestMapping("/getNearbyAttractions")
	public List<Map<String, Object>> getNearbyAttractions(@RequestParam String userName) {
		User user = getUser(userName);
		VisitedLocation visitedLocation = tourGuideService.getUserLocation(user);

		// Use the new method to get the details for the new JSON object
		return tourGuideService.getNearbyAttractionsWithDetails(visitedLocation, user);
	}

	@RequestMapping("/getRewards")
	public List<UserReward> getRewards(@RequestParam String userName) {
		return tourGuideService.getUserRewards(getUser(userName));
	}

	@RequestMapping("/getTripDeals")
	public List<Provider> getTripDeals(@RequestParam String userName) {
		return tourGuideService.getTripDeals(getUser(userName));
	}

	private User getUser(String userName) {
		return tourGuideService.getUser(userName);
	}
}
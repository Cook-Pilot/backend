package com.cookpilot.backend.home;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/home")
@RequiredArgsConstructor
public class HomeRecipeController {

	private final HomeRecipeService homeRecipeService;

	@GetMapping("/recent-recipes")
	public List<RecentRecipeResponse> recentRecipes() {
		return homeRecipeService.findRecentRecipes();
	}
}

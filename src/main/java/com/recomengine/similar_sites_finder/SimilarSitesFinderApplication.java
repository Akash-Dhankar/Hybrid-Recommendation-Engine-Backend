package com.recomengine.similar_sites_finder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class SimilarSitesFinderApplication {
	public static void main(String[] args) {
		SpringApplication.run(SimilarSitesFinderApplication.class, args);
	}

}

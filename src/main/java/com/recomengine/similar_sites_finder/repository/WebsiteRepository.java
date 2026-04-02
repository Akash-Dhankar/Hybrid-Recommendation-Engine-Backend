package com.recomengine.similar_sites_finder.repository;

import com.recomengine.similar_sites_finder.model.Website;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebsiteRepository extends JpaRepository<Website, String> {

    Optional<Website> findByUrl(String url);

    List<Website> findByCategoryIgnoreCaseOrderByMonthlyVisitsDesc(String category);

    @Query("SELECT w FROM Website w ORDER BY w.monthlyVisits DESC NULLS LAST")
    List<Website> findTopByMonthlyVisits(
            org.springframework.data.domain.Pageable pageable
    );

    boolean existsByUrl(String url);
}

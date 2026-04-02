package com.recomengine.similar_sites_finder.repository;

import com.recomengine.similar_sites_finder.model.SimilarSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface SimilarSiteRepository extends JpaRepository<SimilarSite, String> {

    List<SimilarSite> findBySourceUrlOrderByScoreDesc(String sourceUrl);

    boolean existsBySourceUrl(String sourceUrl);

    @Modifying
    @Transactional
    @Query("DELETE FROM SimilarSite s WHERE s.sourceUrl = :sourceUrl")
    void deleteBySourceUrl(@Param("sourceUrl") String sourceUrl);
}

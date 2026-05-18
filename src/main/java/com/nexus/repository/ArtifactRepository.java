package com.nexus.repository;

import com.nexus.model.entity.Artifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

import java.util.List;

@Repository
public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {
    List<Artifact> findByTaskId(UUID taskId);
}

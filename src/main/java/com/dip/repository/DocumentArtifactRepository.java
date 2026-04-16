package com.dip.repository;

import com.dip.domain.DocumentArtifact;
import com.dip.domain.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentArtifactRepository extends JpaRepository<DocumentArtifact, Long> {
    List<DocumentArtifact> findByServiceId(Long serviceId);
    List<DocumentArtifact> findByServiceIdAndDocumentType(Long serviceId, DocumentType documentType);
    List<DocumentArtifact> findByServiceIdAndVersion(Long serviceId, String version);
}

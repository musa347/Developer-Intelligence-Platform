package com.dip.dto;

import com.dip.domain.DocumentType;
import lombok.Data;

@Data
public class IngestRequest {
    private String serviceCode;
    private String content;
    private DocumentType documentType;
    private String version;
    private String sourceReference;
}
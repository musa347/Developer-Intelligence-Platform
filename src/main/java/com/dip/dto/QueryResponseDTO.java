package com.dip.dto;

import lombok.Data;
import java.util.List;

@Data
public class QueryResponseDTO {
    private String answer;
    private List<Source> sources;
    private String confidence;
    private String serviceCode;
}

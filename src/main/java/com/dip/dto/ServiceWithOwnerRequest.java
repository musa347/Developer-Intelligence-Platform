package com.dip.dto;

import com.dip.domain.ServiceStatus;
import lombok.Data;

@Data
public class ServiceWithOwnerRequest {
    private String serviceCode;
    private String name;
    private String domain;
    private String owningTeam;
    private Long ownerId;
    private ServiceStatus status;
}

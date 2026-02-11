package com.wiki.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for hierarchical tree structure of WikiPages
 * Used for sidebar navigation
 */
@Data
public class WikiPageTreeDTO {
    private Long id;
    private String title;
    private String slug;
    private boolean folder;
    private List<WikiPageTreeDTO> children = new ArrayList<>();
}
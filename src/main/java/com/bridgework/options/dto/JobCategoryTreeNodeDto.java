package com.bridgework.options.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class JobCategoryTreeNodeDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String code;
    private final String name;
    private final Integer level;
    private final List<JobCategoryTreeNodeDto> children = new ArrayList<>();

    public JobCategoryTreeNodeDto(String code, String name, Integer level) {
        this.code = code;
        this.name = name;
        this.level = level;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getLevel() {
        return level;
    }

    public List<JobCategoryTreeNodeDto> getChildren() {
        return children;
    }

    public void addChild(JobCategoryTreeNodeDto child) {
        this.children.add(child);
    }
}

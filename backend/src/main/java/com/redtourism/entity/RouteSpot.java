package com.redtourism.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Map;

@Data
@TableName("route_spot")
public class RouteSpot implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long routeId;
    private Long spotId;
    private Integer dayNumber;
    private Integer sortOrder;
    private String description;

    /** 以下字段从 scenic_spot 关联填充，非数据库列 */
    @TableField(exist = false)
    private String spotName;
    @TableField(exist = false)
    private String spotNameEn;
    @TableField(exist = false)
    private String spotNameJa;
    @TableField(exist = false)
    private Double latitude;
    @TableField(exist = false)
    private Double longitude;
    /** 多语言回退标记：key 为发生中文回退的字段名，仅接口响应时填充，非数据库列 */
    @TableField(exist = false)
    private Map<String, Boolean> fallbackFields;
}

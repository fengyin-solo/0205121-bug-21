package com.redtourism.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Data
@TableName("scenic_spot")
public class ScenicSpot implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String nameEn;
    private String nameJa;
    private String description;
    private String descriptionEn;
    private String descriptionJa;
    private String location;
    private String region;
    private String theme;
    private String openTime;
    private BigDecimal ticketPrice;
    private String trafficInfo;
    private String historyBackground;
    private String revolutionEvent;
    private String personStory;
    private String coverImage;
    private Integer status;
    private Long viewCount;
    private Long favoriteCount;
    private Double avgRating;
    private Long commentCount;
    private Long staffId;
    private Double longitude;
    private Double latitude;
    private String ticketReservation;
    private String ticketReservationEn;
    private String ticketReservationJa;
    private String suggestedDuration;
    private String suggestedDurationEn;
    private String suggestedDurationJa;
    private String itemsToBring;
    private String itemsToBringEn;
    private String itemsToBringJa;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /** 多语言回退标记：key 为发生中文回退的字段名，仅接口响应时填充，非数据库列 */
    @TableField(exist = false)
    private Map<String, Boolean> fallbackFields;
}

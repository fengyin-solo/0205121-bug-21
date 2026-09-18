package com.redtourism.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;
import java.util.Map;

@Data
@TableName("culture_content")
public class CultureContent implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String titleEn;
    private String titleJa;
    private String content;
    private String contentEn;
    private String contentJa;
    private Long categoryId;
    private String coverImage;
    private String author;
    private Long viewCount;
    private Long favoriteCount;
    private Long likeCount;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /** 多语言回退标记：key 为发生中文回退的字段名，仅接口响应时填充，非数据库列 */
    @TableField(exist = false)
    private Map<String, Boolean> fallbackFields;
}

package com.homestay3.homestaybackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
// AmenityCategory moved to entity package
import lombok.extern.jackson.Jacksonized;

@Entity
@Table(name = "amenities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Jacksonized
public class Amenity {
    
    @Id
    @Column(name = "`value`", nullable = false, length = 50)
    private String value;
    
    @Column(name = "label", nullable = false, length = 100)
    private String label;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "icon", length = 100)
    private String icon;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_code")
    private AmenityCategory category;
    
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
    
    @Column(name = "usage_count")
    @Builder.Default
    private Long usageCount = 0L;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by", length = 100)
    private String createdBy;
    
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @ManyToMany(mappedBy = "amenities")
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Set<Homestay> homestays = new HashSet<>();
}

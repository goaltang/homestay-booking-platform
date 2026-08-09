package com.homestay3.homestaybackend.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponBatchMessage {

    private Long taskId;
}

package com.homesweet.homesweetback.domain.order.entity;

import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.SkuEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 주문 상품 엔티티
 */
@Entity
@Table(name = "order_items")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private SkuEntity sku;

    @Column(name = "source_cart_id")
    private Long sourceCartId;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false)
    private Long price;

    public Long getTotalPrice() {
        return this.price * this.quantity;
    }
}

package com.homesweet.homesweetback.domain.product.product.command.repository.jpa;

import com.homesweet.homesweetback.domain.product.product.command.repository.jpa.entity.SkuEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SkuJPARepository extends JpaRepository<SkuEntity, Long> {

    @Query("SELECT s FROM SkuEntity s JOIN FETCH s.product WHERE s.id IN :ids")
    List<SkuEntity> findAllByIdWithProduct(@Param("ids") List<Long> ids);

    /**
     * DB 레벨 원자적 재고 차감.
     * stock_quantity >= quantity 조건으로 재고 부족 시 업데이트 0건 반환.
     */
    @Modifying
    @Query("UPDATE SkuEntity s SET s.stockQuantity = s.stockQuantity - :quantity "
            + "WHERE s.id = :skuId AND s.stockQuantity >= :quantity")
    int decreaseStock(@Param("skuId") Long skuId, @Param("quantity") Long quantity);

    /**
     * DB 레벨 원자적 재고 복원.
     */
    @Modifying
    @Query("UPDATE SkuEntity s SET s.stockQuantity = s.stockQuantity + :quantity "
            + "WHERE s.id = :skuId")
    int increaseStock(@Param("skuId") Long skuId, @Param("quantity") Long quantity);
}
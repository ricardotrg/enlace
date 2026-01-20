package com.gps.enlace.repo;

import com.gps.enlace.domain.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceRepo extends JpaRepository<Device, Long> {
    Optional<Device> findByTraccarDeviceId(Long traccarDeviceId);
    
    Optional<Device> findByUniqueId(String uniqueId);

    @Query(value = "SELECT * FROM device WHERE user_id = :userId", nativeQuery = true)
    List<Device> findAllByUserId(@Param("userId") Long userId);

}

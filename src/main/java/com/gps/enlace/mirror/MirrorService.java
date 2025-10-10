package com.gps.enlace.mirror;

import com.gps.enlace.domain.Device;
import com.gps.enlace.domain.MirrorLink;
import com.gps.enlace.live.LiveFix;
import com.gps.enlace.live.PositionCache;
import com.gps.enlace.repo.DeviceRepo;
import com.gps.enlace.repo.MirrorLinkRepo;
import org.springframework.transaction.annotation.Transactional;  // <-- this one
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class MirrorService {
    private final MirrorLinkRepo mirrorRepo;
    private final DeviceRepo deviceRepo;
    private final TokenGenerator tokenGen;
    private final PositionCache cache;

    @Value("${mirror.token.ttl-hours:24}")
    private int ttlHours;

    public MirrorService(MirrorLinkRepo mirrorRepo, DeviceRepo deviceRepo, TokenGenerator tokenGen, PositionCache cache) {
        this.mirrorRepo = mirrorRepo;
        this.deviceRepo = deviceRepo;
        this.tokenGen = tokenGen;
        this.cache = cache;
    }

    public MirrorLink createForTraccarDevice(long traccarDeviceId) {
        Device d = deviceRepo.findByTraccarDeviceId(traccarDeviceId)
                .orElseGet(() -> {
                    Device nd = new Device();
                    nd.setTraccarDeviceId(traccarDeviceId);
                    nd.setName(null);
                    return deviceRepo.save(nd);
                });
        MirrorLink ml = new MirrorLink();
        ml.setToken(tokenGen.generate(48));
        ml.setDevice(d);
        ml.setCreatedAt(OffsetDateTime.now());
        ml.setExpiresAt(OffsetDateTime.now().plusHours(ttlHours));
        return mirrorRepo.save(ml);
    }

    @Transactional
    public Optional<MirrorLink> resolveActive(String token) {
        return mirrorRepo.findByToken(token)
                .filter(ml -> ml.getRevokedAt() == null && ml.getExpiresAt().isAfter(OffsetDateTime.now()));
    }

    @Transactional
    public Optional<LiveFix> latestByToken(String token) {
        return resolveActive(token).flatMap(ml -> cache.get(ml.getDevice().getTraccarDeviceId()));
    }

    @Transactional(readOnly = true)
    public Optional<Long> resolveActiveDeviceId(String token) {
        return mirrorRepo
                .findActiveFetchDevice(token)
                .map(m -> m.getDevice().getTraccarDeviceId());
    }
}

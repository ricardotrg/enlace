-- Tablas mínimas para MVP: device y mirror_link

-- Dispositivo local mapeado a un device de Traccar
CREATE TABLE device (
                        id                BIGSERIAL PRIMARY KEY,
                        traccar_device_id BIGINT      NOT NULL UNIQUE, -- ID numérico en Traccar
                        name              TEXT,
                        created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Enlace espejo público
CREATE TABLE mirror_link (
                             id         BIGSERIAL PRIMARY KEY,
                             token      VARCHAR(64) NOT NULL UNIQUE,        -- token opaco no adivinable
                             device_id  BIGINT      NOT NULL REFERENCES device(id) ON DELETE RESTRICT,
                             created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                             expires_at TIMESTAMPTZ NOT NULL,
                             revoked_at TIMESTAMPTZ,
                             view_count INTEGER     NOT NULL DEFAULT 0,
                             CONSTRAINT mirror_link_expires_after_create CHECK (expires_at > created_at)
);

-- Índices útiles
CREATE INDEX idx_mirror_link_device_id ON mirror_link(device_id);
CREATE INDEX idx_mirror_link_expires_at ON mirror_link(expires_at);

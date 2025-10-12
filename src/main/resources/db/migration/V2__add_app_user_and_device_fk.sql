-- Tabla de usuarios (simple)
CREATE TABLE IF NOT EXISTS app_user (
                                        id             BIGSERIAL PRIMARY KEY,
                                        email          VARCHAR(320) NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    display_name   VARCHAR(120),
    is_admin       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    last_login_at  TIMESTAMP,
    status         SMALLINT NOT NULL DEFAULT 1  -- 1=activo, 0=deshabilitado
    );

-- Relación: device → app_user (uno a muchos)
ALTER TABLE device
    ADD COLUMN IF NOT EXISTS user_id BIGINT;

-- FK (solo si no existe)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_device_user'
  ) THEN
ALTER TABLE device
    ADD CONSTRAINT fk_device_user
        FOREIGN KEY (user_id) REFERENCES app_user(id)
            ON DELETE SET NULL;
END IF;
END $$;

-- Índice para filtrar por usuario
CREATE INDEX IF NOT EXISTS idx_device_user_id ON device(user_id);

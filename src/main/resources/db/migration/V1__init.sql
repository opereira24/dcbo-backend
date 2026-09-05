-- V1__init.sql
-- Esquema base do domínio do back-office (dcbo-backend), derivado das sete coleções
-- Firestore hoje usadas pelo dcbo (cars, clients, transactions, leads, notifications, users,
-- partners) mais a tabela filha car_images. Ver TASK-005 para a derivação campo a campo.
-- Convenções: snake_case, PKs UUID geradas pela BD, TIMESTAMPTZ para datas, NUMERIC(12,2) para
-- valores monetários (nunca tipos de vírgula flutuante).

CREATE TABLE partners (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(255) NOT NULL,
    email             VARCHAR(255),
    phone             VARCHAR(50),
    cars_count        INTEGER NOT NULL DEFAULT 0,
    total_commission  NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE clients (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(255) NOT NULL,
    email             VARCHAR(255),
    phone             VARCHAR(50) NOT NULL,
    nif               VARCHAR(20),
    address           VARCHAR(500),
    postal_code       VARCHAR(20),
    notes             TEXT,
    purchases_count   INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_clients_email ON clients (email);
CREATE INDEX idx_clients_nif ON clients (nif);

CREATE TABLE cars (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    marca             VARCHAR(100) NOT NULL,
    modelo            VARCHAR(100) NOT NULL,
    ano               INTEGER NOT NULL,
    preco             NUMERIC(12,2) NOT NULL,
    km                INTEGER NOT NULL,
    cor               VARCHAR(50) NOT NULL,
    combustivel       VARCHAR(50) NOT NULL,
    transmissao       VARCHAR(50) NOT NULL,
    origem            VARCHAR(100) NOT NULL,
    descricao         TEXT,
    preco_compra      NUMERIC(12,2),
    garantia_meses    INTEGER NOT NULL DEFAULT 0,
    destaque          BOOLEAN NOT NULL DEFAULT false,
    is_consignacao    BOOLEAN NOT NULL DEFAULT false,
    partner_id        UUID REFERENCES partners (id) ON DELETE SET NULL,
    data_compra       TIMESTAMPTZ,
    vendido           BOOLEAN NOT NULL DEFAULT false,
    reservado         BOOLEAN NOT NULL DEFAULT false,
    data_venda        TIMESTAMPTZ,
    preco_venda       NUMERIC(12,2),
    cliente_id        UUID REFERENCES clients (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_cars_vendido_created_at ON cars (vendido, created_at DESC);
CREATE INDEX idx_cars_destaque ON cars (destaque);

CREATE TABLE car_images (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    car_id            UUID NOT NULL REFERENCES cars (id) ON DELETE CASCADE,
    url               VARCHAR(1000) NOT NULL,
    thumbnail_url     VARCHAR(1000),
    position          INTEGER NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_car_images_car_id ON car_images (car_id);

CREATE TABLE transactions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo              VARCHAR(50) NOT NULL,
    valor             NUMERIC(12,2) NOT NULL,
    descricao         VARCHAR(500),
    data              TIMESTAMPTZ NOT NULL,
    car_id            UUID REFERENCES cars (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transactions_car_id ON transactions (car_id);

CREATE TABLE leads (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome              VARCHAR(255) NOT NULL,
    email             VARCHAR(255),
    telefone          VARCHAR(50) NOT NULL,
    mensagem          TEXT,
    carro_id          UUID REFERENCES cars (id) ON DELETE SET NULL,
    carro_marca       VARCHAR(100),
    carro_modelo      VARCHAR(100),
    status            VARCHAR(50) NOT NULL DEFAULT 'novo',
    origem            VARCHAR(100) NOT NULL DEFAULT 'website',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_leads_status_created_at ON leads (status, created_at DESC);

CREATE TABLE notifications (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo              VARCHAR(100) NOT NULL,
    mensagem          TEXT NOT NULL,
    read              BOOLEAN NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_subject      VARCHAR(255) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    name              VARCHAR(255) NOT NULL,
    role              VARCHAR(50) NOT NULL,
    active            BOOLEAN NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_app_users_auth_subject ON app_users (auth_subject);

-- Renames the three foreign-key columns that violated ADR-001 (backlog/CONVENTIONS.md),
-- Camada 1: structural FK columns must always be English (<referenced_table_singular>_id).
-- Renaming in place preserves the existing indexes and `ON DELETE SET NULL` foreign keys untouched.
-- Business attributes (e.g. `marca`, `preco`, `vendido`, `carro_marca`/`carro_modelo`/`carro_preco`)
-- are Camada 2 of the ADR and are NOT renamed here: they mirror the Portuguese keys the React
-- frontends already write, and renaming them would break the existing `dc`/`dcbo` contract.

ALTER TABLE cars RENAME COLUMN cliente_id TO client_id;
ALTER TABLE transactions RENAME COLUMN cliente_id TO client_id;
ALTER TABLE leads RENAME COLUMN carro_id TO car_id;

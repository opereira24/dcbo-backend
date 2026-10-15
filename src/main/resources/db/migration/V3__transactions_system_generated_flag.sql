-- TASK-011 requirement 3: a transaction created automatically by a car sale (CarService#sell)
-- must be distinguishable from a manually-entered one, so CarService#revertSale can remove
-- exactly the transaction it created and never a manual one that happens to share the same
-- tipo/car_id (e.g. a second, manual "venda" entry for the same car).
--
-- Defaults to false so every transaction already in the table (all manually entered so far, since
-- this is the first migration that lets the backend create one automatically) is correctly
-- classified as not system-generated.

ALTER TABLE transactions ADD COLUMN system_generated BOOLEAN NOT NULL DEFAULT false;

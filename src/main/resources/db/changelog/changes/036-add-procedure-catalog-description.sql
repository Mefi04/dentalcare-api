--liquibase formatted sql

--changeset dentalcare:036-add-procedure-catalog-description
ALTER TABLE procedure_catalog_items ADD COLUMN description VARCHAR(1000);

--rollback ALTER TABLE procedure_catalog_items DROP COLUMN description;

--liquibase formatted sql

--changeset author:init
create table person
(
    id         int primary key generated by default as identity,
    name       varchar,
    age        int,
    birthday   timestamp,
    birth_time time
);
create table person_fields
(
    id        int primary key generated by default as identity,
    name      varchar,
    val       varchar,
    person_id integer references person
);
create table product
(
    id         int primary key generated by default as identity,
    name       varchar,
    price      numeric(20, 2),
    rating     integer,
    disabled   boolean,
    created_at timestamp,
    meta_json  jsonb
);
create table content_types
(
    id                          int primary key generated by default as identity,
    data_xml                    varchar,
    data_json                   jsonb,
    data_json_with_extra_fields jsonb
);
create table customer
(
    id         int primary key generated by default as identity,
    name       varchar,
    balance    numeric,
    comment    varchar,
    is_active  boolean,
    created_at timestamp
);

create table cart
(
    id         int primary key generated by default as identity,
    name       varchar
);
create table item
(
    id        int primary key generated by default as identity,
    name      varchar,
    price     numeric,
    cart_id integer references cart
);

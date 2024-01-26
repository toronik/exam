create schema if not exists sa;
set schema sa;
create table if not exists person (id integer primary key, name varchar, age number, iq number, birthday timestamp);
create table if not exists person_fields (id integer primary key, name varchar, val varchar, person_id integer, foreign key (person_id) references person(id));
create table if not exists empty (name varchar, val number);
create table if not exists types (id integer primary key, timestamp_type timestamp, datetime_type smalldatetime, date_type date);

create table if not exists androids_table (id integer primary key, name varchar, height number, weight number, manufactured timestamp);


create table if not exists product (id integer primary key, name varchar, price numeric(20, 2), rating integer, disabled boolean, created_at timestamp, meta_json varchar);

create table if not exists orders (
    id INTEGER primary key,
    status VARCHAR,
    client number,
    driver number,
    created timestamp,
    updated timestamp
);

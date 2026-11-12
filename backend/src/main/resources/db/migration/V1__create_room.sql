create table room (
    id uuid primary key,
    name varchar(100) not null,
    capacity integer not null constraint room_capacity_at_least_one check (capacity >= 1),
    status text not null constraint room_status_known check (status in ('ACTIVE', 'DEACTIVATED'))
);

-- Room names are unique ignoring case. The persistence adapter recognises a
-- violation of this index by its name, written by hand there, because jOOQ
-- generates no constant for a functional index.
create unique index room_name_unique_ignoring_case on room (lower(name));

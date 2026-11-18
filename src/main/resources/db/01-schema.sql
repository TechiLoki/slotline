CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE SCHEMA slotline;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE TABLE slotline.providers(
 id uuid PRIMARY KEY,name text NOT NULL CHECK(length(name)BETWEEN 1 AND 80),role text NOT NULL,zone text NOT NULL,description text NOT NULL DEFAULT '',active boolean NOT NULL DEFAULT true
);
CREATE TABLE slotline.services(
 id uuid PRIMARY KEY,name text NOT NULL,description text NOT NULL,
 duration_minutes integer NOT NULL CHECK(duration_minutes BETWEEN 15 AND 180 AND duration_minutes%15=0),
 buffer_minutes integer NOT NULL CHECK(buffer_minutes BETWEEN 0 AND 60),
 notice_minutes integer NOT NULL CHECK(notice_minutes BETWEEN 0 AND 1440),
 horizon_days integer NOT NULL CHECK(horizon_days BETWEEN 1 AND 90),
 cancel_notice_minutes integer NOT NULL CHECK(cancel_notice_minutes BETWEEN 0 AND 1440),
 active boolean NOT NULL DEFAULT true
);
CREATE TABLE slotline.provider_services(provider_id uuid REFERENCES slotline.providers,service_id uuid REFERENCES slotline.services,PRIMARY KEY(provider_id,service_id));
CREATE TABLE slotline.weekly_hours(provider_id uuid REFERENCES slotline.providers,dow integer NOT NULL CHECK(dow BETWEEN 1 AND 7),opens time NOT NULL,closes time NOT NULL CHECK(closes>opens),PRIMARY KEY(provider_id,dow));
CREATE TABLE slotline.date_overrides(provider_id uuid REFERENCES slotline.providers,day date NOT NULL,closed boolean NOT NULL,opens time,closes time,
 CHECK((closed AND opens IS NULL AND closes IS NULL)OR(NOT closed AND opens IS NOT NULL AND closes IS NOT NULL AND closes>opens)),PRIMARY KEY(provider_id,day));
CREATE TABLE slotline.blocks(id uuid PRIMARY KEY,provider_id uuid NOT NULL REFERENCES slotline.providers,starts_at timestamptz NOT NULL,ends_at timestamptz NOT NULL CHECK(ends_at>starts_at),label text NOT NULL,created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX block_provider_time ON slotline.blocks(provider_id,starts_at);
CREATE TABLE slotline.bookings(
 id uuid PRIMARY KEY,reference text NOT NULL UNIQUE,
 provider_id uuid NOT NULL REFERENCES slotline.providers,service_id uuid NOT NULL REFERENCES slotline.services,
 provider_name text NOT NULL,provider_zone text NOT NULL,service_name text NOT NULL,
 duration_minutes integer NOT NULL,buffer_minutes integer NOT NULL,cancel_notice_minutes integer NOT NULL,
 starts_at timestamptz NOT NULL,ends_at timestamptz NOT NULL,occupied_until timestamptz NOT NULL,
 guest_name text NOT NULL,guest_email text NOT NULL,guest_zone text NOT NULL,note text NOT NULL DEFAULT '',
 manage_hash text NOT NULL,revision integer NOT NULL DEFAULT 0,status text NOT NULL DEFAULT 'confirmed'CHECK(status IN('confirmed','cancelled','completed','no_show')),
 created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now(),
 CHECK(ends_at=starts_at+duration_minutes*interval '1 minute'),CHECK(occupied_until=ends_at+buffer_minutes*interval '1 minute'),
 CHECK(duration_minutes BETWEEN 15 AND 180 AND buffer_minutes BETWEEN 0 AND 60),
 CONSTRAINT no_provider_overlap EXCLUDE USING gist(provider_id WITH =,tstzrange(starts_at,occupied_until,'[)')WITH &&)WHERE(status<>'cancelled')
);
CREATE INDEX booking_calendar ON slotline.bookings(starts_at,provider_id);
CREATE TABLE slotline.request_keys(id uuid PRIMARY KEY,body_hash text NOT NULL,operation text NOT NULL,booking_id uuid NOT NULL REFERENCES slotline.bookings,created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE slotline.booking_events(id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,booking_id uuid NOT NULL REFERENCES slotline.bookings,action text NOT NULL,details jsonb NOT NULL DEFAULT '{}',created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX booking_event_history ON slotline.booking_events(booking_id,id);
INSERT INTO slotline.providers(id,name,role,zone,description)VALUES
 ('10000000-0000-0000-0000-000000000001','Ava Morgan','Product & strategy','Europe/London','Bring a decision, a question, or an idea that needs a second perspective.'),
 ('10000000-0000-0000-0000-000000000002','Nika Reed','Engineering & systems','Asia/Tbilisi','Talk through architecture, technical roadblocks, and your next practical step.'),
 ('10000000-0000-0000-0000-000000000003','Jordan Lee','Design & research','America/New_York','Make room for thoughtful feedback on a product or customer experience.');
INSERT INTO slotline.services(id,name,description,duration_minutes,buffer_minutes,notice_minutes,horizon_days,cancel_notice_minutes)VALUES
 ('20000000-0000-0000-0000-000000000001','Intro consultation','A focused first conversation. Share your goals and find the right next step.',30,15,30,60,60),
 ('20000000-0000-0000-0000-000000000002','Deep-dive session','More room for a complex question, review, or collaborative working session.',60,15,60,60,120),
 ('20000000-0000-0000-0000-000000000003','Follow-up','A short check-in to revisit progress and clear the next hurdle.',15,0,15,30,30);
INSERT INTO slotline.provider_services SELECT p.id,s.id FROM slotline.providers p CROSS JOIN slotline.services s;
INSERT INTO slotline.weekly_hours SELECT p.id,dow,'09:00'::time,'17:00'::time FROM slotline.providers p CROSS JOIN generate_series(1,5)dow;
GRANT USAGE ON SCHEMA slotline TO slotline_app;
GRANT SELECT,UPDATE ON slotline.providers,slotline.services TO slotline_app;
GRANT SELECT ON slotline.provider_services TO slotline_app;
GRANT SELECT,INSERT,UPDATE,DELETE ON slotline.weekly_hours,slotline.date_overrides,slotline.blocks TO slotline_app;
GRANT SELECT,INSERT,UPDATE ON slotline.bookings TO slotline_app;
GRANT SELECT,INSERT ON slotline.request_keys,slotline.booking_events TO slotline_app;
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA slotline TO slotline_app;

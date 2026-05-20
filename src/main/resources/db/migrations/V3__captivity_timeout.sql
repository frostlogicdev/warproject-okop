-- V3: add captured_at to passports for the 30-min captivity auto-release
-- and the escape-attempt timer.
--
-- captured_at is a UNIX-ms epoch. It is set in CaptivityService.capture()
-- alongside captured_by_uuid and trophy, and cleared (set NULL) in
-- CaptivityService.ransom() alongside the same fields.
--
-- The column is nullable on purpose: existing rows pre-migration have no
-- recorded capture moment, and never-captured passports never set it.
-- CaptivityTimeoutService treats NULL as "not captured" — it never matches
-- the auto-release predicate.

ALTER TABLE passports ADD COLUMN captured_at BIGINT;

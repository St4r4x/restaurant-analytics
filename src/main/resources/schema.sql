-- Re-applied on every startup (see spring.sql.init.mode / defer-datasource-initialization
-- in application.properties) because Hibernate's ddl-auto=update creates tables with no
-- knowledge of Postgres Row-Level Security. A fresh database (new environment, restore
-- drill, dropped table) would otherwise come back up unprotected.
ALTER TABLE IF EXISTS public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.bookmarks ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.password_reset_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.inspection_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.audit_log ENABLE ROW LEVEL SECURITY;

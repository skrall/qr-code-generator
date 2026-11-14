-- What a scan of an expired or deleted link gets instead of the destination. Both optional:
-- with neither set the code answers 410 with the default page.
--   gone_url      redirect there instead of showing the page (validated like target_url)
--   gone_message  plain text shown on the 410 page; escaped when rendered, never trusted as HTML
alter table short_link add column gone_url varchar(2048);
alter table short_link add column gone_message varchar(280);

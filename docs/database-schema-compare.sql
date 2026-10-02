-- Generated from all Hibernate entities using MySQLDialect. Read-only.
-- USE the festival database first. Differences require review, not automatic ALTER.
-- Type aliases/display widths and larger existing columns may be harmless differences.
WITH expected AS (
SELECT 'api_request_logs' AS table_name, 'client_ip' AS column_name, 'varchar(45)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'api_request_logs' AS table_name, 'duration_ms' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'api_request_logs' AS table_name, 'host' AS column_name, 'varchar(255)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'api_request_logs' AS table_name, 'http_method' AS column_name, 'varchar(10)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'api_request_logs' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'api_request_logs' AS table_name, 'occurred_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'api_request_logs' AS table_name, 'request_id' AS column_name, 'varchar(36)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'api_request_logs' AS table_name, 'request_path' AS column_name, 'varchar(1024)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'api_request_logs' AS table_name, 'response_status' AS column_name, 'integer' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'api_request_logs' AS table_name, 'route_pattern' AS column_name, 'varchar(512)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'api_request_logs' AS table_name, 'scheme' AS column_name, 'varchar(10)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'api_request_logs' AS table_name, 'user_agent' AS column_name, 'varchar(512)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'api_request_logs' AS table_name, 'user_uuid' AS column_name, 'binary(16)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'bamboo_messages' AS table_name, 'anon_name' AS column_name, 'varchar(20)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_messages' AS table_name, 'content' AS column_name, 'text' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_messages' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_messages' AS table_name, 'deleted_at' AS column_name, 'datetime(6)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'bamboo_messages' AS table_name, 'deleted_by' AS column_name, 'binary(16)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'bamboo_messages' AS table_name, 'hidden_at' AS column_name, 'datetime(6)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'bamboo_messages' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_messages' AS table_name, 'report_count' AS column_name, 'integer' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_messages' AS table_name, 'seq' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_messages' AS table_name, 'status' AS column_name, 'enum (''blocked'',''deleted'',''hidden'',''visible'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_messages' AS table_name, 'user_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_moderation_audits' AS table_name, 'action' AS column_name, 'enum (''mute'',''unmute'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_moderation_audits' AS table_name, 'actor_name' AS column_name, 'varchar(100)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_moderation_audits' AS table_name, 'actor_role' AS column_name, 'enum (''admin'',''booth_manager'',''staff'',''super_admin'',''user'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_moderation_audits' AS table_name, 'actor_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_moderation_audits' AS table_name, 'duration_minutes' AS column_name, 'integer' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'bamboo_moderation_audits' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_moderation_audits' AS table_name, 'occurred_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_moderation_audits' AS table_name, 'reason' AS column_name, 'text' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_moderation_audits' AS table_name, 'source_message_id' AS column_name, 'bigint' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'bamboo_moderation_audits' AS table_name, 'target_nickname' AS column_name, 'varchar(20)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_moderation_audits' AS table_name, 'target_user_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_nicknames' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_nicknames' AS table_name, 'muted_until' AS column_name, 'datetime(6)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'bamboo_nicknames' AS table_name, 'nickname' AS column_name, 'varchar(20)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_nicknames' AS table_name, 'nickname_key' AS column_name, 'varchar(20)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_nicknames' AS table_name, 'user_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_reports' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_reports' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_reports' AS table_name, 'message_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_reports' AS table_name, 'reason' AS column_name, 'enum (''abuse'',''impersonation'',''other'',''personal_info'',''sexual'',''spam'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_reports' AS table_name, 'user_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_settings' AS table_name, 'closes_at' AS column_name, 'datetime(6)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'bamboo_settings' AS table_name, 'enabled' AS column_name, 'bit' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_settings' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_settings' AS table_name, 'read_only' AS column_name, 'bit' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'bamboo_settings' AS table_name, 'updated_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'birthday_message_hearts' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'birthday_message_hearts' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'birthday_message_hearts' AS table_name, 'message_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'birthday_message_hearts' AS table_name, 'user_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'birthday_messages' AS table_name, 'author_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'birthday_messages' AS table_name, 'content' AS column_name, 'text' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'birthday_messages' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'birthday_messages' AS table_name, 'design_no' AS column_name, 'int' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'birthday_messages' AS table_name, 'heart_count' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'birthday_messages' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'birthday_messages' AS table_name, 'public_department' AS column_name, 'text' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'birthday_messages' AS table_name, 'public_masked_name' AS column_name, 'text' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'birthday_messages' AS table_name, 'public_masked_student_no' AS column_name, 'text' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_favorites' AS table_name, 'booth_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_favorites' AS table_name, 'festival_user_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_favorites' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_managers' AS table_name, 'booth_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_managers' AS table_name, 'festival_user_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_media' AS table_name, 'booth_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_media' AS table_name, 'display_order' AS column_name, 'integer' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_booth_media' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_media' AS table_name, 'is_representative' AS column_name, 'bit' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_media' AS table_name, 'media_kind' AS column_name, 'enum (''image'',''video'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_media' AS table_name, 'media_url' AS column_name, 'varchar(2048)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_media' AS table_name, 'original_filename' AS column_name, 'varchar(255)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_booth_media' AS table_name, 'storage_key' AS column_name, 'varchar(512)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_stamps' AS table_name, 'booth_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_stamps' AS table_name, 'festival_user_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_stamps' AS table_name, 'grant_method' AS column_name, 'enum (''admin_search'',''qr'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_stamps' AS table_name, 'granted_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_stamps' AS table_name, 'granted_by' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booth_stamps' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booths' AS table_name, 'category' AS column_name, 'varchar(32)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booths' AS table_name, 'closes_at' AS column_name, 'time(0)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booths' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booths' AS table_name, 'created_by' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booths' AS table_name, 'description' AS column_name, 'text' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booths' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booths' AS table_name, 'latitude' AS column_name, 'decimal(10,7)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booths' AS table_name, 'longitude' AS column_name, 'decimal(10,7)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booths' AS table_name, 'name' AS column_name, 'varchar(150)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booths' AS table_name, 'opens_at' AS column_name, 'time(0)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booths' AS table_name, 'operator' AS column_name, 'varchar(150)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booths' AS table_name, 'stamp_enabled' AS column_name, 'tinyint(1)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booths' AS table_name, 'updated_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_booths' AS table_name, 'updated_by' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performance_links' AS table_name, 'display_order' AS column_name, 'integer' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performance_links' AS table_name, 'link_url' AS column_name, 'varchar(2048)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performance_links' AS table_name, 'performance_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performance_media' AS table_name, 'display_order' AS column_name, 'integer' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_performance_media' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performance_media' AS table_name, 'media_kind' AS column_name, 'enum (''image'',''video'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performance_media' AS table_name, 'media_source' AS column_name, 'enum (''link'',''upload'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performance_media' AS table_name, 'media_url' AS column_name, 'varchar(2048)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performance_media' AS table_name, 'original_filename' AS column_name, 'varchar(255)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_performance_media' AS table_name, 'performance_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performance_media' AS table_name, 'storage_key' AS column_name, 'varchar(512)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_performance_members' AS table_name, 'display_order' AS column_name, 'integer' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performance_members' AS table_name, 'member_name' AS column_name, 'varchar(100)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performance_members' AS table_name, 'performance_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performances' AS table_name, 'category' AS column_name, 'enum (''celebrity'',''club'',''individual'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performances' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performances' AS table_name, 'created_by' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performances' AS table_name, 'description' AS column_name, 'text' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performances' AS table_name, 'ends_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performances' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performances' AS table_name, 'published_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performances' AS table_name, 'starts_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performances' AS table_name, 'team_name' AS column_name, 'varchar(150)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performances' AS table_name, 'updated_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_performances' AS table_name, 'updated_by' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_answer_options' AS table_name, 'answer_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_answer_options' AS table_name, 'option_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_answers' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_answers' AS table_name, 'question_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_answers' AS table_name, 'submission_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_answers' AS table_name, 'text_value' AS column_name, 'text' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_poll_options' AS table_name, 'display_order' AS column_name, 'integer' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_options' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_options' AS table_name, 'image_original_filename' AS column_name, 'varchar(255)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_poll_options' AS table_name, 'image_storage_key' AS column_name, 'varchar(1024)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_poll_options' AS table_name, 'image_url' AS column_name, 'varchar(2048)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_poll_options' AS table_name, 'option_text' AS column_name, 'text' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_options' AS table_name, 'question_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_question_media' AS table_name, 'display_order' AS column_name, 'integer' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_question_media' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_question_media' AS table_name, 'kind' AS column_name, 'enum (''image'',''video'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_question_media' AS table_name, 'media_url' AS column_name, 'varchar(2048)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_question_media' AS table_name, 'original_filename' AS column_name, 'varchar(255)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_question_media' AS table_name, 'question_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_question_media' AS table_name, 'storage_key' AS column_name, 'varchar(1024)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_questions' AS table_name, 'display_order' AS column_name, 'integer' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_questions' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_questions' AS table_name, 'poll_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_questions' AS table_name, 'question_text' AS column_name, 'text' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_questions' AS table_name, 'required' AS column_name, 'bit' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_questions' AS table_name, 'type' AS column_name, 'enum (''long_text'',''multiple_choice'',''short_text'',''single_choice'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_submissions' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_submissions' AS table_name, 'poll_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_submissions' AS table_name, 'single_vote_key' AS column_name, 'binary(16)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_poll_submissions' AS table_name, 'submitted_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_poll_submissions' AS table_name, 'user_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'allow_multiple_submissions' AS column_name, 'bit' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'anonymous' AS column_name, 'bit' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'closed_at' AS column_name, 'datetime(6)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'created_by' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'description' AS column_name, 'text' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'ends_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'image_original_filename' AS column_name, 'varchar(255)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'image_storage_key' AS column_name, 'varchar(1024)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'image_url' AS column_name, 'varchar(2048)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'published_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'result_published_at' AS column_name, 'datetime(6)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'starts_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'title' AS column_name, 'varchar(200)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'updated_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_polls' AS table_name, 'updated_by' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_qr_tokens' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_qr_tokens' AS table_name, 'expires_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_qr_tokens' AS table_name, 'token_hash' AS column_name, 'varchar(64)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_qr_tokens' AS table_name, 'user_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_schedules' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_schedules' AS table_name, 'created_by' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_schedules' AS table_name, 'ends_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_schedules' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_schedules' AS table_name, 'performance_id' AS column_name, 'bigint' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_schedules' AS table_name, 'published_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_schedules' AS table_name, 'starts_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_schedules' AS table_name, 'title' AS column_name, 'varchar(150)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_schedules' AS table_name, 'updated_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_schedules' AS table_name, 'updated_by' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_sponsors' AS table_name, 'booth_id' AS column_name, 'bigint' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_sponsors' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_sponsors' AS table_name, 'created_by' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_sponsors' AS table_name, 'description' AS column_name, 'text' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_sponsors' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_sponsors' AS table_name, 'image_url' AS column_name, 'varchar(2048)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_sponsors' AS table_name, 'name' AS column_name, 'varchar(100)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_sponsors' AS table_name, 'storage_key' AS column_name, 'varchar(1024)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_sponsors' AS table_name, 'updated_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_sponsors' AS table_name, 'updated_by' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_sponsors' AS table_name, 'version' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_events' AS table_name, 'action' AS column_name, 'enum (''grant'',''revoke'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_events' AS table_name, 'actor_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_events' AS table_name, 'booth_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_events' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_events' AS table_name, 'method' AS column_name, 'enum (''admin_search'',''qr'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_events' AS table_name, 'occurred_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_events' AS table_name, 'target_user_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prize_events' AS table_name, 'action' AS column_name, 'enum (''grant'',''revoke'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prize_events' AS table_name, 'actor_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prize_events' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prize_events' AS table_name, 'method' AS column_name, 'enum (''admin_search'',''qr'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prize_events' AS table_name, 'occurred_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prize_events' AS table_name, 'prize_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prize_events' AS table_name, 'reason' AS column_name, 'text' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prize_events' AS table_name, 'stamp_count' AS column_name, 'integer' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prizes' AS table_name, 'granted_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prizes' AS table_name, 'granted_by' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prizes' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prizes' AS table_name, 'issued' AS column_name, 'tinyint(1)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prizes' AS table_name, 'stamp_count' AS column_name, 'integer' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prizes' AS table_name, 'target_user_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_stamp_prizes' AS table_name, 'version' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'booth_manager' AS column_name, 'tinyint(1)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'management_role' AS column_name, 'enum(''super_admin'',''admin'',''staff'',''user'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'school_subject_hash' AS column_name, 'varchar(64)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'school_verification_status' AS column_name, 'enum(''unverified'',''verified'',''revoked'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'school_verified_at' AS column_name, 'datetime(6)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'student_fee_paid' AS column_name, 'tinyint(1)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'student_fee_subject_hash' AS column_name, 'varchar(64)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'updated_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'user_uuid' AS column_name, 'char(36)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'welcome_email_claim_token' AS column_name, 'varchar(36)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'welcome_email_claimed_at' AS column_name, 'datetime(6)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'welcome_email_pending' AS column_name, 'tinyint(1)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_users' AS table_name, 'welcome_email_sent_at' AS column_name, 'datetime(6)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_wristband_events' AS table_name, 'action' AS column_name, 'enum (''issue'',''revoke'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristband_events' AS table_name, 'actor_name' AS column_name, 'varchar(200)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristband_events' AS table_name, 'actor_uuid' AS column_name, 'char(36)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristband_events' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristband_events' AS table_name, 'occurred_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristband_events' AS table_name, 'reason' AS column_name, 'text' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_wristband_events' AS table_name, 'target_user_uuid' AS column_name, 'char(36)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristband_events' AS table_name, 'wristband_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristbands' AS table_name, 'active_user_uuid' AS column_name, 'char(36)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'festival_wristbands' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristbands' AS table_name, 'issued' AS column_name, 'bit' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristbands' AS table_name, 'issued_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristbands' AS table_name, 'issued_by' AS column_name, 'char(36)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristbands' AS table_name, 'issuer_name' AS column_name, 'varchar(200)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristbands' AS table_name, 'subject_hash' AS column_name, 'varchar(64)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristbands' AS table_name, 'target_name' AS column_name, 'varchar(200)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristbands' AS table_name, 'target_user_uuid' AS column_name, 'char(36)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristbands' AS table_name, 'updated_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'festival_wristbands' AS table_name, 'version' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'frontend_event_logs' AS table_name, 'app_version' AS column_name, 'varchar(50)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'frontend_event_logs' AS table_name, 'client_occurred_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'frontend_event_logs' AS table_name, 'duration_ms' AS column_name, 'bigint' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'frontend_event_logs' AS table_name, 'event_id' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'frontend_event_logs' AS table_name, 'event_type' AS column_name, 'enum (''booth_detail_view'',''external_link_click'',''map_view'',''notice_detail_view'',''page_leave'',''page_view'',''performance_detail_view'',''qr_page_view'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'frontend_event_logs' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'frontend_event_logs' AS table_name, 'received_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'frontend_event_logs' AS table_name, 'request_id' AS column_name, 'varchar(36)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'frontend_event_logs' AS table_name, 'route' AS column_name, 'varchar(200)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'frontend_event_logs' AS table_name, 'session_id' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'frontend_event_logs' AS table_name, 'target_id' AS column_name, 'varchar(100)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'frontend_event_logs' AS table_name, 'user_uuid' AS column_name, 'binary(16)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'lost_item_notice_images' AS table_name, 'display_order' AS column_name, 'integer' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'lost_item_notice_images' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notice_images' AS table_name, 'notice_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notice_images' AS table_name, 'original_filename' AS column_name, 'varchar(255)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notice_images' AS table_name, 'storage_key' AS column_name, 'varchar(512)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notice_images' AS table_name, 'url' AS column_name, 'varchar(2048)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notices' AS table_name, 'author_name' AS column_name, 'varchar(100)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notices' AS table_name, 'author_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notices' AS table_name, 'content' AS column_name, 'text' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notices' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notices' AS table_name, 'found_location' AS column_name, 'varchar(200)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'lost_item_notices' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notices' AS table_name, 'last_modified_by_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notices' AS table_name, 'pinned' AS column_name, 'bit' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notices' AS table_name, 'pinned_at' AS column_name, 'datetime(6)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'lost_item_notices' AS table_name, 'status' AS column_name, 'enum (''holding'',''returned'')' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notices' AS table_name, 'title' AS column_name, 'varchar(150)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notices' AS table_name, 'updated_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'lost_item_notices' AS table_name, 'view_count' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notice_attachments' AS table_name, 'content_type' AS column_name, 'varchar(150)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notice_attachments' AS table_name, 'display_order' AS column_name, 'integer' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'notice_attachments' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notice_attachments' AS table_name, 'notice_id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notice_attachments' AS table_name, 'original_filename' AS column_name, 'varchar(255)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notice_attachments' AS table_name, 'size' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notice_attachments' AS table_name, 'storage_key' AS column_name, 'varchar(512)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notice_attachments' AS table_name, 'url' AS column_name, 'varchar(2048)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notices' AS table_name, 'author_name' AS column_name, 'varchar(100)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notices' AS table_name, 'author_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notices' AS table_name, 'banner' AS column_name, 'tinyint(1)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notices' AS table_name, 'content' AS column_name, 'text' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notices' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notices' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notices' AS table_name, 'last_modified_by_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notices' AS table_name, 'pinned' AS column_name, 'bit' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notices' AS table_name, 'pinned_at' AS column_name, 'datetime(6)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'notices' AS table_name, 'title' AS column_name, 'varchar(150)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notices' AS table_name, 'updated_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'notices' AS table_name, 'view_count' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'school_verification_requests' AS table_name, 'current_department' AS column_name, 'varchar(100)' AS expected_type, 'YES' AS expected_nullable
UNION ALL
SELECT 'school_verification_requests' AS table_name, 'current_name' AS column_name, 'varchar(100)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'school_verification_requests' AS table_name, 'current_student_no' AS column_name, 'varchar(50)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'school_verification_requests' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'school_verification_requests' AS table_name, 'requested_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'school_verification_requests' AS table_name, 'school_department' AS column_name, 'varchar(100)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'school_verification_requests' AS table_name, 'school_name' AS column_name, 'varchar(100)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'school_verification_requests' AS table_name, 'school_student_no' AS column_name, 'varchar(50)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'school_verification_requests' AS table_name, 'school_subject_hash' AS column_name, 'varchar(64)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'school_verification_requests' AS table_name, 'school_verified_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'school_verification_requests' AS table_name, 'user_uuid' AS column_name, 'binary(16)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'student_fee_lock' AS table_name, 'id' AS column_name, 'bigint' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'student_fee_payers' AS table_name, 'created_at' AS column_name, 'datetime(6)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'student_fee_payers' AS table_name, 'created_by' AS column_name, 'varchar(36)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'student_fee_payers' AS table_name, 'student_no' AS column_name, 'varchar(10)' AS expected_type, 'NO' AS expected_nullable
UNION ALL
SELECT 'student_fee_payers' AS table_name, 'subject_hash' AS column_name, 'varchar(64)' AS expected_type, 'NO' AS expected_nullable
)
SELECT e.*, c.COLUMN_TYPE AS actual_type, c.IS_NULLABLE AS actual_nullable,
 c.COLLATION_NAME, c.CHARACTER_MAXIMUM_LENGTH, c.CHARACTER_OCTET_LENGTH,
 CASE WHEN c.COLUMN_NAME IS NULL THEN 'MISSING' ELSE 'REVIEW' END AS finding
FROM expected e LEFT JOIN information_schema.COLUMNS c
 ON c.TABLE_SCHEMA = DATABASE() AND c.TABLE_NAME = e.table_name AND c.COLUMN_NAME = e.column_name
WHERE c.COLUMN_NAME IS NULL OR LOWER(c.COLUMN_TYPE) <> e.expected_type
 OR c.IS_NULLABLE <> e.expected_nullable
ORDER BY e.table_name, e.column_name;

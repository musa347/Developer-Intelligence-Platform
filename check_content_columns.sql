-- Check if content columns exist in database tables
-- Run this script to verify content persistence

-- Check document_artifacts table schema
SELECT column_name, data_type, is_nullable 
FROM information_schema.columns 
WHERE table_name = 'document_artifacts' 
AND column_name IN ('content', 'content_length', 'chunk_count')
ORDER BY ordinal_position;

-- Check document_chunks table schema  
SELECT column_name, data_type, is_nullable 
FROM information_schema.columns 
WHERE table_name = 'document_chunks' 
AND column_name IN ('content', 'content_length', 'section', 'vector_id')
ORDER BY ordinal_position;

-- Check if content has data in document_artifacts
SELECT 
    id,
    content_length,
    CASE WHEN content IS NULL THEN 'NULL' 
         WHEN content = '' THEN 'EMPTY' 
         ELSE 'HAS_DATA' END as content_status,
    LEFT(content, 100) as content_preview
FROM document_artifacts 
ORDER BY id DESC 
LIMIT 5;

-- Check if content has data in document_chunks
SELECT 
    id,
    artifact_id,
    content_length,
    CASE WHEN content IS NULL THEN 'NULL' 
         WHEN content = '' THEN 'EMPTY' 
         ELSE 'HAS_DATA' END as content_status,
    LEFT(content, 100) as content_preview
FROM document_chunks 
ORDER BY id DESC 
LIMIT 10;

-- Count records with content
SELECT 
    'document_artifacts' as table_name,
    COUNT(*) as total_records,
    COUNT(CASE WHEN content IS NOT NULL AND content != '' THEN 1 END) as records_with_content
FROM document_artifacts
UNION ALL
SELECT 
    'document_chunks' as table_name,
    COUNT(*) as total_records,
    COUNT(CASE WHEN content IS NOT NULL AND content != '' THEN 1 END) as records_with_content
FROM document_chunks;

#!/usr/bin/env python3
from pymongo import MongoClient
import os

# 환경변수에서 MongoDB URI 가져오기 (또는 기본값 사용)
MONGO_URI = os.environ.get("MONGO_URI", "mongodb://localhost:27017/omniwrite")
client = MongoClient(MONGO_URI)

db_name = "omniwrite"
print(f"Connecting to database '{db_name}'...")

# 기존 데이터베이스 삭제 (주의: 기존 데이터 모두 삭제됨)
client.drop_database(db_name)
print(f"Database '{db_name}' dropped.")

# 새 데이터베이스 생성
db = client[db_name]

# 생성할 컬렉션 목록
collections = [
    "stt_results",
    "files",
    "ocr_results",
    "questions",
    "summaries",
    "translations"
]

for coll in collections:
    try:
        db.create_collection(coll)
        print(f"Collection '{coll}' created.")
    except Exception as e:
        print(f"Collection '{coll}' creation failed: {e}")

# 예시: 인덱스 생성 (필요에 따라 추가)
db.files.create_index("user_id")
db.stt_results.create_index("user_id")
db.ocr_results.create_index("document_id")

client.close()
print("Database initialization completed.")

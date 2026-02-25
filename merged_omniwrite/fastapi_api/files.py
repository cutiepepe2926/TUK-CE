from fastapi import APIRouter, UploadFile, File, Depends
from datetime import datetime
from db import file_collection
from fastapi.responses import FileResponse
import os

router = APIRouter()

UPLOAD_DIR = "uploads"

@router.post("/files/upload/")
async def upload_file(file: UploadFile = File(...), user_id: int = 1):
    file_location = f"{UPLOAD_DIR}/{user_id}/{datetime.utcnow().strftime('%Y%m%d%H%M%S')}_{file.filename}"
    os.makedirs(os.path.dirname(file_location), exist_ok=True)

    with open(file_location, "wb") as buffer:
        buffer.write(file.file.read())

    file_doc = {
        "user_id": user_id,
        "filename": file.filename,
        "file_type": "original",
        "stored_path": file_location,
        "uploaded_at": datetime.utcnow()
    }
    file_collection.insert_one(file_doc)
    return {"message": "파일 업로드 성공", "file_path": file_location}

@router.post("/files/upload/converted/")
async def upload_converted_file(file: UploadFile = File(...), user_id: int = 1):
    file_location = f"{UPLOAD_DIR}/{user_id}/converted/{datetime.utcnow().strftime('%Y%m%d%H%M%S')}_{file.filename}"
    os.makedirs(os.path.dirname(file_location), exist_ok=True)

    with open(file_location, "wb") as buffer:
        buffer.write(file.file.read())

    file_doc = {
        "user_id": user_id,
        "filename": file.filename,
        "file_type": "converted",
        "stored_path": file_location,
        "uploaded_at": datetime.utcnow()
    }
    file_collection.insert_one(file_doc)
    return {"message": "변환된 파일 업로드 성공", "file_path": file_location}

@router.get("/files/download/")
async def download_file(filename: str, user_id: int = 1):
    file_data = file_collection.find_one({"filename": filename, "user_id": user_id})
    if not file_data:
        return {"error": "파일을 찾을 수 없습니다."}
    return FileResponse(file_data["stored_path"], filename=filename, media_type="application/octet-stream")

@router.get("/files/list/")
async def list_files(user_id: int = 1):
    files = list(file_collection.find({"user_id": user_id}, {"_id": 0, "user_id": 0}))
    return {"files": files}

@router.delete("/files/delete/")
async def delete_file(filename: str, user_id: int = 1):
    file_data = file_collection.find_one({"filename": filename, "user_id": user_id})
    if not file_data:
        return {"error": "삭제할 파일을 찾을 수 없습니다."}

    if os.path.exists(file_data["stored_path"]):
        os.remove(file_data["stored_path"])

    file_collection.delete_one({"filename": filename, "user_id": user_id})
    return {"message": "파일 삭제 성공"}

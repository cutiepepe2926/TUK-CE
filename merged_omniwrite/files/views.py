import os
import datetime
import logging
from django.conf import settings
from django.http import FileResponse, Http404
from django.core.files.storage import default_storage
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from pymongo import MongoClient
from rest_framework import status
from rest_framework.decorators import api_view

# 로깅 설정
logger = logging.getLogger(__name__)

# ✅ MongoDB 연결
MONGO_URI = os.getenv("MONGO_URI", "mongodb://localhost:27017/omniwrite")
client = MongoClient(MONGO_URI, authSource="admin")
db = client["omniwrite"]
files_collection = db["files"]

# ✅ 파일 저장 함수 (폴더별로 정리)
def save_file(file_obj, user_id, file_type):
    timestamp = datetime.datetime.utcnow().strftime("%Y%m%d%H%M%S")
    ext = os.path.splitext(file_obj.name)[1]
    file_dir = os.path.join(settings.MEDIA_ROOT, str(user_id), file_type)  # 계정별 폴더 생성
    file_path = os.path.join(file_dir, f"{timestamp}{ext}")

    # 폴더가 없으면 생성
    os.makedirs(file_dir, exist_ok=True)

    # 파일 저장
    saved_path = default_storage.save(file_path, file_obj)
    return saved_path

# ✅ 원본 파일 업로드 API
class FileUploadView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        file_obj = request.FILES.get("file")
        if not file_obj:
            return Response({"error": "파일이 제공되지 않았습니다."}, status=status.HTTP_400_BAD_REQUEST)

        user_id = request.user.id
        saved_path = save_file(file_obj, user_id, "original")

        # MongoDB에 파일 메타데이터 저장
        files_collection.insert_one({
            "user_id": user_id,
            "file_type": "original",
            "uri": saved_path,
            "uploaded_at": datetime.datetime.utcnow()
        })

        return Response({"message": "파일 업로드 성공", "file_path": saved_path}, status=status.HTTP_201_CREATED)


# ✅ 변환본 (OCR, STT, 번역, 요약) 업로드 API
class UploadTextDataView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        try:
            file_type = request.data.get("type")  # OCR, STT, 번역, 요약 등
            content = request.data.get("content")
            if not file_type or not content:
                return Response({"error": "데이터 유형과 내용이 필요합니다."}, status=400)

            user_id = request.user.id
            timestamp = datetime.datetime.utcnow().strftime("%Y%m%d%H%M%S")
            file_path = os.path.join("uploads", str(user_id), file_type, f"{timestamp}.txt")

            # 폴더 생성 후 파일 저장
            os.makedirs(os.path.dirname(file_path), exist_ok=True)
            with open(file_path, "w", encoding="utf-8") as f:
                f.write(content)

            # MongoDB에 변환본 정보 저장
            db.text_data.insert_one({
                "user_id": user_id,
                "file_type": file_type,
                "uri": file_path,
                "uploaded_at": datetime.datetime.utcnow()
            })

            return Response({"message": "텍스트 데이터 업로드 성공", "file_path": file_path}, status=201)
        except Exception as e:
            return Response({"error": f"텍스트 업로드 중 오류 발생: {str(e)}"}, status=500)


# ✅ 파일 다운로드 API (계정별 폴더에서 가져옴)
class FileDownloadView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        user_id = request.user.id
        filename = request.GET.get("filename")
        file_type = request.GET.get("file_type")

        if not filename or not file_type:
            return Response({"error": "파일명과 파일 유형을 입력하세요."}, status=status.HTTP_400_BAD_REQUEST)

        file_path = os.path.join("uploads", str(user_id), file_type, filename)

        if not os.path.exists(file_path):
            logger.warning(f"파일 찾을 수 없음: {file_path}")
            raise Http404("파일을 찾을 수 없습니다.")

        return FileResponse(open(file_path, "rb"), as_attachment=True, filename=filename)


# ✅ 파일 목록 조회 (계정별 파일만 조회)
@api_view(["GET"])
def list_files(request):
    user_id = request.user.id
    try:
        files = list(files_collection.find({"user_id": user_id}, {"_id": 0, "file_type": 1, "uri": 1, "uploaded_at": 1}))
        return Response({"files": files}, status=status.HTTP_200_OK)
    except Exception as e:
        return Response({"error": str(e)}, status=status.HTTP_500_INTERNAL_SERVER_ERROR)


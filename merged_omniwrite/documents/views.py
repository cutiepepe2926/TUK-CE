from django.http import FileResponse, Http404
from django.conf import settings
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from pymongo import MongoClient
import datetime
import os

# MongoDB 연결
MONGO_URI = os.environ.get("MONGO_URI", "mongodb://localhost:27017/omniwrite")
client = MongoClient(MONGO_URI)
db = client["omniwrite"]

class UploadTextDataView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        try:
            data_type = request.data.get("type")
            content = request.data.get("content")
            if not data_type or not content:
                return Response({"error": "데이터 유형과 내용이 필요합니다."}, status=400)

            db.text_data.insert_one({
                "user_id": request.user.id,
                "type": data_type,
                "content": content,
                "uploaded_at": datetime.datetime.utcnow()
            })

            return Response({"message": "텍스트 데이터 업로드 성공"}, status=201)
        except Exception as e:
            return Response({"error": f"텍스트 업로드 중 오류 발생: {str(e)}"}, status=500)

class DownloadTextDataView(APIView):
    def get(self, request, data_type):
        filename = request.GET.get('filename')  # 파일명을 쿼리 파라미터에서 가져옴 ✅
        if not filename:
            return Response({"error": "filename parameter is required"}, status=400)

        file_path = os.path.join(settings.MEDIA_ROOT, "documents", data_type, filename)

        if os.path.exists(file_path):
            return FileResponse(open(file_path, 'rb'), as_attachment=True)
        else:
            raise Http404

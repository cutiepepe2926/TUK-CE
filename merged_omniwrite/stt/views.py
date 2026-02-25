import os
import datetime
import logging
import torch
import whisper
import subprocess
from django.conf import settings
from django.core.files.storage import default_storage
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from pymongo import MongoClient
from celery import shared_task

logger = logging.getLogger(__name__)

device = "cuda" if torch.cuda.is_available() else "cpu"
whisper_model = whisper.load_model("small", device=device)
whisper_model.eval()

def convert_to_wav(input_path):
    output_path = os.path.splitext(input_path)[0] + "_converted.wav"
    command = ["ffmpeg", "-y", "-threads", "2", "-i", input_path, output_path]
    try:
        subprocess.run(command, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        return output_path
    except Exception as e:
        logger.error(f"WAV 변환 오류: {e}")
        raise

@shared_task
def process_stt_task(job_id, file_path):
    try:
        local_path = os.path.join(settings.MEDIA_ROOT, file_path)
        wav_path = convert_to_wav(local_path)
        transcript = whisper_model.transcribe(wav_path, language="ko").get("text", "")

        client = MongoClient(os.environ.get("MONGO_URI", "mongodb://localhost:27017/omniwrite"), connect=False)
        db = client["omniwrite"]
        stt_collection = db["stt_results"]
        stt_collection.update_one({"_id": job_id}, {"$set": {"transcript": transcript}})
    except Exception as e:
        logger.error(f"STT 처리 오류: {e}")

class STTView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        file = request.FILES.get("audio")
        if not file:
            return Response({"error": "No file provided"}, status=400)

        job_id = str(datetime.datetime.now().timestamp())
        file_path = default_storage.save(f"uploads/{job_id}.wav", file)
        process_stt_task.delay(job_id, file_path)  # Celery를 통해 비동기 처리
        return Response({"message": "Processing started", "job_id": job_id}, status=202)

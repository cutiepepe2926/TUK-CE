import os
import subprocess
import logging
import torch
import whisper

logger = logging.getLogger(__name__)
device = "cuda" if torch.cuda.is_available() else "cpu"
try:
    whisper_model = whisper.load_model("medium", device=device)
    whisper_model.eval()
except Exception as e:
    logger.error(f"Whisper 모델 로드 실패: {e}")
    raise

def transcribe_audio(audio_path):
    try:
        logger.info(f"Whisper로 STT 변환 시작: {audio_path}")
        result = whisper_model.transcribe(audio_path, language="ko")
        transcript = result.get("text", "")
        logger.info("STT 변환 완료")
        return transcript
    except Exception as e:
        logger.error(f"STT 변환 오류: {e}")
        raise

def convert_to_wav(input_path):
    output_path = os.path.splitext(input_path)[0] + "_converted.wav"
    command = ["ffmpeg", "-y", "-i", input_path, output_path]
    try:
        subprocess.run(command, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        logger.info(f"WAV 변환 완료: {output_path}")
        return output_path
    except subprocess.CalledProcessError as e:
        error_msg = e.stderr.decode() if e.stderr else "변환 오류 발생"
        logger.error(f"WAV 변환 중 오류: {error_msg}")
        raise


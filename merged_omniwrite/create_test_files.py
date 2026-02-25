import os

TEST_FILES_DIR = "/tmp/omniwrite_test_files/"

def create_test_files():
    """테스트용 파일 자동 생성"""
    os.makedirs(TEST_FILES_DIR, exist_ok=True)

    # 원본 파일 생성 (PDF, WAV, JPG)
    with open(os.path.join(TEST_FILES_DIR, "document.pdf"), "wb") as f:
        f.write(os.urandom(2048))  # 랜덤 바이너리 데이터 생성
    with open(os.path.join(TEST_FILES_DIR, "audio.wav"), "wb") as f:
        f.write(os.urandom(1024))
    with open(os.path.join(TEST_FILES_DIR, "image.jpg"), "wb") as f:
        f.write(os.urandom(1024))

    # 변환된 텍스트 파일 생성 (OCR, STT, 번역, 요약)
    with open(os.path.join(TEST_FILES_DIR, "document_ocr.txt"), "w") as f:
        f.write("This is an OCR converted text.")
    with open(os.path.join(TEST_FILES_DIR, "audio_stt.txt"), "w") as f:
        f.write("This is an STT converted text.")
    with open(os.path.join(TEST_FILES_DIR, "translated.txt"), "w") as f:
        f.write("This is a translated text.")
    with open(os.path.join(TEST_FILES_DIR, "summary.txt"), "w") as f:
        f.write("This is a summarized text.")

    print(f"✅ 테스트 파일이 {TEST_FILES_DIR} 폴더에 생성되었습니다.")

# 실행
if __name__ == "__main__":
    create_test_files()

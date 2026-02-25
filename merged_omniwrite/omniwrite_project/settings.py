import os
from pathlib import Path
from dotenv import load_dotenv
import dj_database_url

# 환경 변수 로드
load_dotenv()

BASE_DIR = Path(__file__).resolve().parent.parent

# 보안 설정
SECRET_KEY = os.getenv("SECRET_KEY", "your-secure-secret-key")
DEBUG = os.getenv("DEBUG", "True") == "True"

ALLOWED_HOSTS = [
    "127.0.0.1",
    "localhost",
    "www.omniwrite.r-e.kr",
    os.getenv("SERVER_IP", ""),
]

# 데이터베이스 설정
DATABASES = {
    "default": dj_database_url.config(default="sqlite:///db.sqlite3")
}

# ✅ MongoDB 연결 정보 환경 변수에서 가져오기
MONGO_URI = os.getenv("MONGO_URI", "mongodb://localhost:27017/omniwrite?authSource=admin")

# Django 기본 ORM 비활성화
INSTALLED_APPS = [
    "accounts",
    "rest_framework",
    "rest_framework_simplejwt.token_blacklist",
    "stt",
    "files",
    "documents",
    "django.contrib.staticfiles",
    "django.contrib.contenttypes",
    "django.contrib.auth",
    "django.contrib.admin",
    "django.contrib.sessions",
    "django.contrib.messages",
]

# JWT 설정
from datetime import timedelta

SIMPLE_JWT = {
    "ACCESS_TOKEN_LIFETIME": timedelta(minutes=30),
    "REFRESH_TOKEN_LIFETIME": timedelta(days=7),
    "ROTATE_REFRESH_TOKENS": True,
    "BLACKLIST_AFTER_ROTATION": True,
    "AUTH_HEADER_TYPES": ("Bearer",),
}

# CORS 설정
CORS_ALLOW_CREDENTIALS = True
CORS_ALLOWED_ORIGINS = [
    "https://www.omniwrite.r-e.kr",
    "http://localhost:8000",
]
CORS_ALLOW_HEADERS = [
    "authorization",
    "content-type",
    "x-csrf-token",
]

# CSRF 설정
CSRF_TRUSTED_ORIGINS = [
    "https://www.omniwrite.r-e.kr",
    "http://localhost:8000",
]

# 파일 업로드 크기 제한
DATA_UPLOAD_MAX_MEMORY_SIZE = 524288000
FILE_UPLOAD_MAX_MEMORY_SIZE = 524288000

# 정적 파일 설정
STATIC_URL = "/static/"
STATIC_ROOT = os.path.join(BASE_DIR, "static")

# 미디어 파일 설정
MEDIA_ROOT = os.path.join(BASE_DIR, "media")
MEDIA_URL = "/media/"

# WSGI 설정
WSGI_APPLICATION = "omniwrite_project.wsgi.application"
ROOT_URLCONF = "omniwrite_project.urls"
AUTH_USER_MODEL = "accounts.User"

# DRF 인증 방식
REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": (
        "rest_framework_simplejwt.authentication.JWTAuthentication",
    ),
}

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [os.path.join(BASE_DIR, "templates")],  # 필요하면 수정
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.debug',
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

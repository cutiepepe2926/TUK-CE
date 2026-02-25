# config/wsgi.py
import os
from django.core.wsgi import get_wsgi_application

# 사용할 settings 모듈을 지정합니다.
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'config.production')
application = get_wsgi_application()


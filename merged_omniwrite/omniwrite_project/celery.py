import os
from celery import Celery

# Django의 기본 설정을 Celery에 적용
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'omniwrite_project.settings')

app = Celery('omniwrite_project')

# Celery 설정을 Django 설정에서 가져오기
app.config_from_object('django.conf:settings', namespace='CELERY')

# 자동으로 tasks.py 파일을 찾아 실행
app.autodiscover_tasks()

@app.task(bind=True)
def debug_task(self):
    print(f'Request: {self.request!r}')

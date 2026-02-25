import os
import sys

sys.path.append('/home/juno/omniwrite')  # 프로젝트 경로 추가
sys.path.append('/home/juno/omniwrite/omniwrite_project')  # 프로젝트 내부 경로 추가

from django.core.wsgi import get_wsgi_application

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'omniwrite_project.settings')
application = get_wsgi_application()


from django.urls import path
from .views import STTView

urlpatterns = [
    path('', STTView.as_view(), name='stt-process'),  # 추가
    path('upload/', STTView.as_view(), name='stt-upload'),
]


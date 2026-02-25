from django.urls import path
from .views import FileUploadView, FileDownloadView  # 여기서 오류 발생 가능

urlpatterns = [
    path('upload/', FileUploadView.as_view(), name='file-upload'),
    path('download/', FileDownloadView.as_view(), name='file-download'),  # 다운로드 추가
]



from django.urls import path
from .views import UploadTextDataView, DownloadTextDataView  # ✅ 올바르게 import

urlpatterns = [
    path('upload_text/', UploadTextDataView.as_view(), name='upload-text'),  # ✅ 업로드 API
    path('download_text/<str:data_type>/', DownloadTextDataView.as_view(), name='download-text'),  # ✅ 다운로드 API
]

"""
URL configuration for omniwrite_project project.

The `urlpatterns` list routes URLs to views. For more information please see:
    https://docs.djangoproject.com/en/5.1/topics/http/urls/
Examples:
Function views
    1. Add an import:  from my_app import views
    2. Add a URL to urlpatterns:  path('', views.home, name='home')
Class-based views
    1. Add an import:  from other_app.views import Home
    2. Add a URL to urlpatterns:  path('', Home.as_view(), name='home')
Including another URLconf
    1. Import the include() function: from django.urls import include, path
    2. Add a URL to urlpatterns:  path('blog/', include('blog.urls'))
"""
from django.contrib import admin
from django.urls import path, include
from django.http import JsonResponse
from django.conf import settings
from django.conf.urls.static import static
from rest_framework_simplejwt.views import TokenRefreshView  
from files.views import list_files
def index(request):
    return JsonResponse({"message": "Welcome to Omniwrite API!"})


urlpatterns = [

    path("", index, name="index"),  
    path('api/documents/', include('documents.urls')),  
     path("api/accounts/token/refresh/", TokenRefreshView.as_view(), name="token_refresh"),  
    path('admin/', admin.site.urls),
    path("api/accounts/", include("accounts.urls")),
    path("api/stt/", include("stt.urls")), 
    path("api/files/", include("files.urls")), 
     path("api/files/list/", list_files, name="file-list"),
    path("api/accounts/", include("accounts.urls")),
] + static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)


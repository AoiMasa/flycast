/*
 *  Created on: Oct 3, 2019

	Copyright 2019 flyinghead

	This file is part of Flycast.

    Flycast is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 2 of the License, or
    (at your option) any later version.

    Flycast is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Flycast.  If not, see <https://www.gnu.org/licenses/>.
*/
#pragma once
#include <memory>
#include <unordered_set>
#include "vulkan_context.h"
#include "buffer.h"
#include "rend/TexCache.h"
#include "hw/pvr/Renderer_if.h"

void setImageLayout(vk::CommandBuffer const& commandBuffer, vk::Image image, vk::Format format, u32 mipmapLevels, vk::ImageLayout oldImageLayout, vk::ImageLayout newImageLayout);

struct Texture : BaseTextureCacheData
{
	void UploadToGPU(int width, int height, u8 *data, bool mipmapped, bool mipmapsIncluded = false) override;
	u64 GetIntId() { return (u64)reinterpret_cast<uintptr_t>(this); }
	std::string GetId() override { char s[20]; sprintf(s, "%p", this); return s; }
	bool IsNew() const { return !image.get(); }
	vk::ImageView GetImageView() const { return *imageView; }
	vk::Image GetImage() const { return *image; }
	vk::ImageView GetReadOnlyImageView() const { return readOnlyImageView ? readOnlyImageView : *imageView; }
	void SetCommandBuffer(vk::CommandBuffer commandBuffer) { this->commandBuffer = commandBuffer; }
	virtual bool Force32BitTexture(TextureType type) const override { return !VulkanContext::Instance()->IsFormatSupported(type); }

	void SetPhysicalDevice(vk::PhysicalDevice physicalDevice) { this->physicalDevice = physicalDevice; }
	void SetDevice(vk::Device device) { this->device = device; }

private:
	void Init(u32 width, u32 height, vk::Format format ,u32 dataSize, bool mipmapped, bool mipmapsIncluded);
	void SetImage(u32 size, void *data, bool isNew, bool genMipmaps);
	void CreateImage(vk::ImageTiling tiling, vk::ImageUsageFlags usage, vk::ImageLayout initialLayout,
			vk::MemoryPropertyFlags memoryProperties, vk::ImageAspectFlags aspectMask);
	void GenerateMipmaps();

	vk::Format format = vk::Format::eUndefined;
	vk::Extent2D extent;
	u32 mipmapLevels = 1;
	bool needsStaging = false;
	std::unique_ptr<BufferData> stagingBufferData;
	vk::CommandBuffer commandBuffer;

	Allocation allocation;
	vk::UniqueImage image;
	vk::UniqueImageView imageView;
	vk::ImageView readOnlyImageView;

	vk::PhysicalDevice physicalDevice;
	vk::Device device;

	friend class TextureDrawer;
	friend class OITTextureDrawer;
	friend class TextureCache;
};

class SamplerManager
{
public:
	vk::Sampler GetSampler(TSP tsp)
	{
		u32 samplerHash = tsp.full & TSP_Mask;	// MipMapD, FilterMode, ClampU, ClampV, FlipU, FlipV
		const auto& it = samplers.find(samplerHash);
		vk::Sampler sampler;
		if (it != samplers.end())
			return it->second.get();
		vk::Filter filter = tsp.FilterMode == 0 ? vk::Filter::eNearest : vk::Filter::eLinear;
		vk::SamplerAddressMode uRepeat = tsp.ClampU ? vk::SamplerAddressMode::eClampToEdge
				: tsp.FlipU ? vk::SamplerAddressMode::eMirroredRepeat : vk::SamplerAddressMode::eRepeat;
		vk::SamplerAddressMode vRepeat = tsp.ClampV ? vk::SamplerAddressMode::eClampToEdge
				: tsp.FlipV ? vk::SamplerAddressMode::eMirroredRepeat : vk::SamplerAddressMode::eRepeat;

		bool anisotropicFiltering = settings.rend.AnisotropicFiltering > 1 && VulkanContext::Instance()->SupportsSamplerAnisotropy()
				&& filter == vk::Filter::eLinear;
		return samplers.emplace(
					std::make_pair(samplerHash, VulkanContext::Instance()->GetDevice().createSamplerUnique(
						vk::SamplerCreateInfo(vk::SamplerCreateFlags(), filter, filter,
							vk::SamplerMipmapMode::eNearest, uRepeat, vRepeat, vk::SamplerAddressMode::eClampToEdge, D_Adjust_LoD_Bias[tsp.MipMapD],
							anisotropicFiltering, std::min((float)settings.rend.AnisotropicFiltering, VulkanContext::Instance()->GetMaxSamplerAnisotropy()),
							false, vk::CompareOp::eNever,
							0.0f, 256.0f, vk::BorderColor::eFloatOpaqueBlack)))).first->second.get();
	}
	static const u32 TSP_Mask = 0x7ef00;

private:
	std::map<u32, vk::UniqueSampler> samplers;
};

class FramebufferAttachment
{
public:
	FramebufferAttachment(vk::PhysicalDevice physicalDevice, vk::Device device)
		: physicalDevice(physicalDevice), device(device), format(vk::Format::eUndefined)
		{}
	void Init(u32 width, u32 height, vk::Format format, vk::ImageUsageFlags usage);
	void Reset() { image.reset(); imageView.reset(); }

	vk::ImageView GetImageView() const { return *imageView; }
	vk::Image GetImage() const { return *image; }
	const BufferData* GetBufferData() const { return stagingBufferData.get(); }
	vk::ImageView GetStencilView() const { return *stencilView; }
	vk::Extent2D getExtent() const { return extent; }

private:
	vk::Format format;
	vk::Extent2D extent;

	std::unique_ptr<BufferData> stagingBufferData;
	Allocation allocation;
	vk::UniqueImage image;
	vk::UniqueImageView imageView;
	vk::UniqueImageView stencilView;

	vk::PhysicalDevice physicalDevice;
	vk::Device device;
};

class TextureCache : public BaseTextureCache<Texture>
{
public:
	Texture *getTextureCacheData(TSP tsp, TCW tcw)
	{
		Texture *texture = BaseTextureCache<Texture>::getTextureCacheData(tsp, tcw);
		inFlightTextures[currentIndex].insert(texture);
		return texture;
	}

	void SetCurrentIndex(int index) {
		if (currentIndex < inFlightTextures.size())
			std::for_each(inFlightTextures[currentIndex].begin(), inFlightTextures[currentIndex].end(),
				[](Texture *texture) { texture->readOnlyImageView = vk::ImageView(); });
		currentIndex = index;
		EmptyTrash(inFlightTextures);
		EmptyTrash(trashedImageViews);
		EmptyTrash(trashedImages);
		EmptyTrash(trashedMem);
		EmptyTrash(trashedBuffers);
	}

	bool IsInFlight(Texture *texture)
	{
		return std::any_of(inFlightTextures.begin(), inFlightTextures.end(),
				[texture](const std::unordered_set<Texture *>& set) { return set.find(texture) != set.end(); });
	}

	void DestroyLater(Texture *texture)
	{
		if (!texture->image)
			return;
		trashedImages[currentIndex].push_back(std::move(texture->image));
		trashedImageViews[currentIndex].push_back(std::move(texture->imageView));
		trashedMem[currentIndex].push_back(std::move(texture->allocation));
		trashedBuffers[currentIndex].push_back(std::move(texture->stagingBufferData));
		texture->format = vk::Format::eUndefined;
	}

private:
	template<typename T>
	void EmptyTrash(T& v)
	{
		if (v.size() < currentIndex + 1)
			v.resize(currentIndex + 1);
		v[currentIndex].clear();
	}
	std::vector<std::unordered_set<Texture *>> inFlightTextures;
	std::vector<std::vector<vk::UniqueImageView>> trashedImageViews;
	std::vector<std::vector<vk::UniqueImage>> trashedImages;
	std::vector<std::vector<Allocation>> trashedMem;
	std::vector<std::vector<std::unique_ptr<BufferData>>> trashedBuffers;
	int currentIndex = 0;
};

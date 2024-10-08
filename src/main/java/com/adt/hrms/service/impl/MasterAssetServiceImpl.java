package com.adt.hrms.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adt.hrms.model.AssetAttribute;
import com.adt.hrms.model.AssetAttributeMapping;
import com.adt.hrms.model.AssetInfo;
import com.adt.hrms.model.AssetStatus;
import com.adt.hrms.model.AssetType;
import com.adt.hrms.model.Employee;
import com.adt.hrms.repository.AssetAttributeMappingRepo;
import com.adt.hrms.repository.AssetAttributeRepo;
import com.adt.hrms.repository.AssetInfoRepo;
import com.adt.hrms.repository.AssetStatusRepo;
import com.adt.hrms.repository.AssetTypeRepo;
import com.adt.hrms.repository.EmployeeRepo;
import com.adt.hrms.request.AssetAttributeMappingDTO;
import com.adt.hrms.request.AssetDTO;
import com.adt.hrms.request.AssetInfoDTO;
import com.adt.hrms.request.AssignAssetsDTO;
import com.adt.hrms.request.EmployeeDTO;
import com.adt.hrms.request.ResponseDTO;
import com.adt.hrms.service.MasterAssetService;
import com.adt.hrms.util.TableDataExtractor;

@Service
public class MasterAssetServiceImpl implements MasterAssetService {

	private final Logger log = LoggerFactory.getLogger(this.getClass());

	private static final int MAX_PAGE_SIZE = 50;
	private static final int DEFAULT_PAGE_SIZE = 10;

	@Autowired
	private TableDataExtractor dataExtractor;

	@Autowired
	private AssetTypeRepo assetTypeRepo;

	@Autowired
	private AssetInfoRepo assetInfoRepo;

	@Autowired
	private AssetAttributeRepo assetAttributeRepo;

	@Autowired
	private AssetAttributeMappingRepo assetAttributeMappingRepo;

	@Autowired
	private EmployeeRepo employeeRepo;

	@Autowired
	private AssetStatusRepo assetStatusRepo;

	private ResponseDTO buildResponse(String status, String message, Object data) {
		ResponseDTO responseDTO = new ResponseDTO();
		responseDTO.setStatus(status);
		responseDTO.setMessage(message);
		responseDTO.setData(data);
		return responseDTO;
	}

	private boolean containsDigit(String str) {
		for (char c : str.toCharArray()) {
			if (Character.isDigit(c)) {
				return true;
			}
		}
		return false;
	}

	@Transactional
	public synchronized String generateAssetADTId(String assetName) {
		Optional<String> abbreviationOpt = assetTypeRepo.findAbbreviationByAssetName(assetName.toUpperCase());

		if (!abbreviationOpt.isPresent()) {
			throw new IllegalArgumentException("No abbreviation found for given asset name");
		}

		String abbreviation = abbreviationOpt.get().toUpperCase();
		String prefix = "ADT_" + abbreviation + "_";
		String sql = "SELECT asset_adt_id FROM employee_schema.asset WHERE asset_adt_id LIKE '" + prefix
				+ "%' ORDER BY asset_adt_id DESC LIMIT 1";
		List<Map<String, Object>> adtIdData = dataExtractor.extractDataFromTable(sql);

		int newIdNumber = 1;
		if (!adtIdData.isEmpty()) {
			String lastId = (String) adtIdData.get(0).get("asset_adt_id");
			String numberPart = lastId.substring(prefix.length());
			newIdNumber = Integer.parseInt(numberPart) + 1;
		}

		return String.format("%s%04d", prefix, newIdNumber);
	}

	@Override
	public ResponseDTO addAssetType(AssetType assetType) {
		log.info("MasterAssetServiceImpl:addAssetType info level log message");

		try {
			if (assetType == null) {
				throw new IllegalArgumentException("Asset type should not be null or invalid");
			}

			String assetName = assetType.getAssetName();
			String assetAbbreviation = assetType.getAssetAbbreviation();

			String upperAssetName = assetName.toUpperCase();
			String upperAssetAbbreviation = assetAbbreviation.toUpperCase();

			if (assetName == null || assetName.trim().isEmpty() || assetName.length() > 50) {
				throw new IllegalArgumentException(
						"Asset type name should not be null, empty, or greater than 50 characters");
			}

			if (assetAbbreviation == null || assetAbbreviation.trim().isEmpty() || assetAbbreviation.length() < 2
					|| assetAbbreviation.length() > 10) {
				throw new IllegalArgumentException(
						"Asset abbreviation should not be null, empty, or it should not be less than 2 or greater than 10 characters");
			}

			Optional<AssetType> assetTypeExistByName = assetTypeRepo.findByAssetName(upperAssetName);
			if (assetTypeExistByName.isPresent()) {
				return buildResponse("AlreadyExist",
						"This asset type name is already exists, please enter a unique asset type name", null);
			}

			Optional<AssetType> assetTypeExistByAbbreviation = assetTypeRepo
					.findByAssetAbbreviation(upperAssetAbbreviation);
			if (assetTypeExistByAbbreviation.isPresent()) {
				return buildResponse("AlreadyExist",
						"This asset abbreviation is already assigned to another asset type, please enter a unique asset abbreviation",
						null);
			}

			assetType.setAssetName(upperAssetName);
			assetType.setAssetAbbreviation(upperAssetAbbreviation);
			assetTypeRepo.save(assetType);

			return buildResponse("Success", "Asset type saved successfully", null);

		} catch (IllegalArgumentException e) {
			log.error("addAssetType IllegalArgumentException: {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("MasterAssetServiceImpl: addAssetType Exception: {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occured", null);
		}
	}

	@Override
	public ResponseDTO getAssetTypeById(Integer assetTypeId) {
		log.info("MasterAssetServiceImpl:getAssetTypeById info level log message");
		try {
			if (assetTypeId == 0 || assetTypeId.equals("")) {
				throw new IllegalArgumentException("Provide valid AssetTypeId, it should not be 0 or invalid or null");
			}

			Optional<AssetType> assetType = assetTypeRepo.findAssetByAssetTypeId(assetTypeId);

			if (assetType.isEmpty()) {
				return buildResponse("NotFound", "Asset type not found", null);
			} else {
				return buildResponse("Success", "Asset type fetched successfully", assetType.get());
			}
		} catch (IllegalArgumentException e) {
			log.error("getAssetTypeById IllegalArgumentException : {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("MasterAssetServiceImpl: getAssetTypeById Exception : {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occured", null);
		}
	}

	@Override
	public ResponseDTO getAllAssetType() {
		log.info("MasterAssetServiceImpl:masterAsset:getAllAssetType info level log message");
		try {
			List<AssetType> assetTypesList = assetTypeRepo.findAll();

			if (assetTypesList.isEmpty()) {
				return buildResponse("NotFound", "Asset type not found", null);
			} else {
				return buildResponse("Success", "All asset type fetched successfully", assetTypesList);
			}
		} catch (Exception e) {
			log.error("getAllAssetType Exception : {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occured", null);
		}
	}

	@Override
	public ResponseDTO updateAssetTypeById(Integer assetTypeId, AssetType assetType) {
		log.info("MasterAssetServiceImpl:updateAssetTypeById info level log message");

		try {
			if (assetTypeId == null || assetTypeId == 0) {
				throw new IllegalArgumentException(
						"Provide valid Asset type id, it should not be 0 or invalid or null");
			}

			String assetName = assetType.getAssetName();
			String assetAbbreviation = assetType.getAssetAbbreviation();

			if (assetName == null || assetName.trim().isEmpty() || assetName.length() > 50) {
				throw new IllegalArgumentException(
						"Asset type name should not be null, invalid, or greater than 50 characters");
			}

			if (assetAbbreviation == null || assetAbbreviation.trim().isEmpty() || assetAbbreviation.length() < 2
					|| assetAbbreviation.length() > 10) {
				throw new IllegalArgumentException(
						"Asset abbreviation should not be null, empty, or it should not be less than 2 or greater than 10 characters");
			}

			Optional<AssetType> existingAssetType = assetTypeRepo.findAssetByAssetTypeId(assetTypeId);

			if (existingAssetType.isPresent()) {
				AssetType assetToUpdate = existingAssetType.get();
				if (assetName != null && !assetName.trim().isEmpty()) {
					assetToUpdate.setAssetName(assetName.toUpperCase());
				}
				if (assetAbbreviation != null && !assetAbbreviation.trim().isEmpty()) {
					assetToUpdate.setAssetAbbreviation(assetAbbreviation.toUpperCase());
				}

				assetTypeRepo.save(assetToUpdate);
				return buildResponse("Success", "Asset type updated successfully", null);
			} else {
				return buildResponse("NotFound", "Asset type not found", null);
			}
		} catch (IllegalArgumentException e) {
			log.error("updateAssetTypeById IllegalArgumentException: {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("MasterAssetServiceImpl: updateAssetTypeById Exception: {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occured", null);
		}
	}

	@Override
	public ResponseDTO deleteAssetTypeById(Integer assetTypeId) {
		log.info("MasterAssetServiceImpl:deleteAssetTypeById info level log message");
		try {
			if (assetTypeId == 0 || assetTypeId.equals("")) {
				throw new IllegalArgumentException(
						"Provide valid Asset type id, it should not be 0 or invalid or null");
			}

			Optional<List<AssetInfo>> assetInfoListExist = assetInfoRepo.findAssetInfoListByAssetTypeId(assetTypeId);
			if (assetInfoListExist.isPresent() && !assetInfoListExist.get().isEmpty()) {
				for (AssetInfo assetInfo : assetInfoListExist.get()) {
					Optional<List<AssetAttributeMapping>> assetAttributeMappingListExist = assetAttributeMappingRepo
							.findAssetAttributeMappingListByAssetInfoId(assetInfo.getId());
					if (assetAttributeMappingListExist.isPresent() && !assetAttributeMappingListExist.get().isEmpty()) {

						return buildResponse("AlreadyAssociated", "This asset is already in use, you can't delete this",
								null);
					}
				}
				return buildResponse("AlreadyAssociated", "This asset is already in use, you can't delete this", null);
			}

			Optional<List<AssetAttribute>> assetAttributeListExist = assetAttributeRepo
					.findAssetAttributeByAssetTypeId(assetTypeId);
			if (assetAttributeListExist.isPresent() && !assetAttributeListExist.get().isEmpty()) {
				return buildResponse("AlreadyAssociated", "This asset is already in use, you can't delete this", null);
			}

			Optional<AssetType> assetTypeExist = assetTypeRepo.findAssetByAssetTypeId(assetTypeId);
			if (assetTypeExist.isPresent()) {

				if (!assetInfoListExist.isPresent() && assetInfoListExist.get().isEmpty()) {
					assetInfoRepo.deleteByAssetTypeId(assetTypeId);
				}

				if (!assetAttributeListExist.isPresent() && assetAttributeListExist.get().isEmpty()) {
					assetAttributeRepo.deleteByAssetTypeId(assetTypeId);
				}

				assetTypeRepo.delete(assetTypeExist.get());

				return buildResponse("Success", "Asset type deleted successfully", null);
			} else {
				return buildResponse("NotFound", "Asset type not found", null);
			}

		} catch (IllegalArgumentException e) {
			log.error("deleteAssetTypeById IllegalArgumentException : {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("MasterAssetServiceImpl: deleteAssetTypeById Exception: {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occured", null);
		}
	}

	@Override
	public ResponseDTO addAssetAttributesByAssetTypeId(Integer assetTypeId, String assetAttributeName) {
		log.info("MasterAssetServiceImpl:addAssetAttributesByAssetTypeId info level log message");
		try {
			if (assetTypeId == 0 || assetTypeId.equals("")) {
				throw new IllegalArgumentException(
						"Provide valid Asset type id, it should not be 0 or invalid or null");
			}
			if ((assetAttributeName == null || assetAttributeName.equals("")) || (assetAttributeName.length() > 50)) {
				throw new IllegalArgumentException(
						"Asset attribute name should not be null or invalid, it should not be greater than 50 characters ");
			}

			Optional<AssetType> existingAssetType = assetTypeRepo.findAssetByAssetTypeId(assetTypeId);
			if (existingAssetType.isPresent()) {

				if (assetAttributeName != null && !assetAttributeName.equals("")) {

					AssetAttribute assetAttributeSave = new AssetAttribute();
					assetAttributeSave.setAssetAttributeName(assetAttributeName.toUpperCase());
					assetAttributeSave.setAsset_type_id(assetTypeId);

					assetAttributeRepo.save(assetAttributeSave);
					return buildResponse("Success", "Asset attribute saved successfully", null);

				} else {
					return buildResponse("NotSaved", "Asset attribute not saved yet", null);
				}
			} else {
				return buildResponse("NotFound", "Asset type not found", null);
			}
		} catch (IllegalArgumentException e) {
			log.error("addAssetAttributesByAssetTypeId IllegalArgumentException : {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("MasterAssetServiceImpl: addAssetAttributesByAssetTypeId Exception : {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occured", null);
		}
	}

	@Override
	public ResponseDTO getAllAssetAttributesByAssetTypeId(Integer assetTypeId) {
		log.info("MasterAssetServiceImpl : getAllAssetAttributesByAssetTypeId info level log message");
		try {
			if (assetTypeId == 0 || assetTypeId.equals("")) {
				throw new IllegalArgumentException(
						"Provide valid Asset type id, it should not be 0 or invalid or null");
			}

			List<AssetAttribute> assetAttributeList = assetAttributeRepo
					.findAllAssetAttributesByAssetTypeId(assetTypeId);

			if (assetAttributeList.isEmpty()) {
				return buildResponse("NotFound", "No asset attributes found", null);
			} else {
				return buildResponse("Success", "Asset attributes fetched successfully", assetAttributeList);
			}
		} catch (IllegalArgumentException e) {
			log.error("getAllAssetAttributesByAssetTypeId IllegalArgumentException : {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("getAllAssetAttributesByAssetTypeId Exception : {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occured", null);
		}
	}

	@Override
	public ResponseDTO updateAssetAttributeById(Integer assetAttributeId, Integer assetTypeId,
			String assetAttributeName) {
		log.info("MasterAssetServiceImpl:updateAssetAttributeById info level log message");
		try {
			if (assetTypeId == 0 || assetTypeId.equals("")) {
				throw new IllegalArgumentException(
						"Provide valid Asset type id, it should not be 0 or invalid or null");
			}
			if (assetAttributeId == 0 || assetAttributeId.equals("")) {
				throw new IllegalArgumentException(
						"Provide valid Asset attribute id, it should not be 0 or invalid or null");
			}
			if ((assetAttributeName == null || assetAttributeName.equals("")) || (assetAttributeName.length() > 50)) {
				throw new IllegalArgumentException(
						"Asset attribute name should not be null or invalid, and it should not be greater than 50 characters ");
			}

			Optional<AssetAttribute> assetAttributeExist = assetAttributeRepo.findById(assetAttributeId);

			if (assetAttributeExist.isPresent()) {

				assetAttributeExist.get().setAssetAttributeName(assetAttributeName.toUpperCase());
				assetAttributeExist.get().getAssetType();
				assetAttributeExist.get().setAsset_type_id(assetTypeId);

				assetAttributeRepo.save(assetAttributeExist.get());
				return buildResponse("Success", "Asset attribute updated successfully", null);
			} else {
				return buildResponse("NotFound", "Asset attribute not found ", null);
			}
		} catch (IllegalArgumentException e) {
			log.error("updateAssetAttributeById IllegalArgumentException : {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("MasterAssetServiceImpl: updateAssetAttributeById Exception : {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occured", null);
		}
	}

	@Override
	public ResponseDTO deleteAssetAttributeById(Integer assetAttributeId) {
		log.info("MasterAssetServiceImpl:deleteAssetAttributeById info level log message");
		try {
			if (assetAttributeId == 0 || assetAttributeId.equals("")) {
				throw new IllegalArgumentException(
						"Provide valid Asset attribute id,it should not be 0 or invalid or null");
			}

			Optional<AssetAttribute> assetAttributeExist = assetAttributeRepo.findById(assetAttributeId);

			if (assetAttributeExist.isPresent()) {

				List<AssetAttributeMapping> mappingListExist = assetAttributeMappingRepo
						.findMappingListByAtrributeId(assetAttributeId).orElse(Collections.emptyList());
				if (!mappingListExist.isEmpty()) {
					return buildResponse("AlreadyAssociated",
							"Asset is already created using this attribute, you can't delete this", null);
				}
				assetAttributeRepo.deleteById(assetAttributeId);
				return buildResponse("Success", "Asset attribute deleted successfully", null);

			} else {
				return buildResponse("NotFound", "Asset attribute not found ", null);
			}
		} catch (IllegalArgumentException e) {
			log.error("deleteAssetAttributeById IllegalArgumentException : {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("MasterAssetServiceImpl: deleteAssetAttributeById Exception: {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occured", null);
		}
	}

	@Transactional
	@Override
	public ResponseDTO saveAssetInfo(AssetDTO assetDTO) {
		log.info("MasterAssetServiceImpl:saveAssetInfo info level log message");
		try {
			if (assetDTO.getAssetTypeName() == null || assetDTO.getAssetTypeName().isBlank()) {
				throw new IllegalArgumentException("Provide valid Asset type name, it should not be null or invalid");
			}
			if (assetDTO.getAssetStatus() == null || assetDTO.getAssetStatus().isBlank()) {
				throw new IllegalArgumentException("Provide valid Asset status, it should not be null or invalid");
			}
			if (assetDTO.getAssetStatus().toUpperCase().equalsIgnoreCase("ALLOTTED")) {
				throw new IllegalArgumentException("Asset status cannot be marked as allotted for the first time");
			}
			if (assetDTO.getAssetAttributeMappingList() == null || assetDTO.getAssetAttributeMappingList().isEmpty()) {
				throw new IllegalArgumentException(
						"Provide valid Asset attribute mapping list, it should not be null or invalid");
			}

			for (AssetAttributeMappingDTO assetAttributeMapping : assetDTO.getAssetAttributeMappingList()) {
				if (assetAttributeMapping.getAssetAttributeName() == null
						|| assetAttributeMapping.getAssetAttributeName().isBlank()
						|| assetAttributeMapping.getAssetAttributeName().equals("")
						|| assetAttributeMapping.getAssetAttributeName().length() > 50) {
					throw new IllegalArgumentException(
							"Asset attribute name should not be null, blank, or greater than 50 characters");
				}
				if (assetAttributeMapping.getAssetAttributeValue() == null
						|| assetAttributeMapping.getAssetAttributeValue().isBlank()
						|| assetAttributeMapping.getAssetAttributeValue().length() > 50) {
					throw new IllegalArgumentException(
							"Asset attribute value should not be null, blank, or greater than 50 characters");
				}
			}

			Optional<AssetType> existingAssetType = assetTypeRepo
					.findByAssetName(assetDTO.getAssetTypeName().toUpperCase());
			if (existingAssetType.isPresent()) {

				String assetADTId = generateAssetADTId(existingAssetType.get().getAssetName());

				AssetInfo asset = new AssetInfo();
				asset.setAsset_type_id(existingAssetType.get().getId());
				asset.setAssetADT_ID(assetADTId);

				Optional<AssetStatus> assetStatusExist = assetStatusRepo
						.findAssetStatus(assetDTO.getAssetStatus().toUpperCase());
				if (assetStatusExist.isEmpty()) {
					asset.setAsset_status_id(null);
				} else {
					asset.setAsset_status_id(assetStatusExist.get().getId());
				}

				if (assetDTO.getEmpId() == null || assetDTO.getEmpId().equals("")) {
					asset.setEmp_id(null);
				} else {
					asset.setEmp_id(assetDTO.getEmpId());
				}
				AssetInfo savedAssetInfo = assetInfoRepo.save(asset);

				if (savedAssetInfo != null && !savedAssetInfo.equals("")) {

					for (AssetAttributeMappingDTO assetMappingDTO : assetDTO.getAssetAttributeMappingList()) {
						AssetAttributeMapping assetMappingSaved = new AssetAttributeMapping();

						assetMappingSaved.setAsset_id(savedAssetInfo.getId());

						Optional<AssetAttribute> attributeExist = assetAttributeRepo.findAttributeByName(
								assetMappingDTO.getAssetAttributeName().toUpperCase(), existingAssetType.get().getId());
						if (attributeExist.isPresent()) {
							assetMappingSaved.setAsset_attribute_id(attributeExist.get().getId());
						} else {
							assetMappingSaved.setAsset_attribute_id(Integer.parseInt("0"));
						}
						assetMappingSaved
								.setAssetAttributeValue(assetMappingDTO.getAssetAttributeValue().toUpperCase());
						assetAttributeMappingRepo.save(assetMappingSaved);
					}

					return buildResponse("Success", "Asset info saved successfully", null);
				} else {
					return buildResponse("NotSaved", "Asset info not saved yet", null);
				}
			} else {
				return buildResponse("NotFound", "Asset type not found", null);
			}
		} catch (IllegalArgumentException e) {
			log.error("saveAssetInfo IllegalArgumentException : {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("saveAssetInfo Exception : {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occured", null);
		}
	}

	@Override
	public ResponseDTO getAllAssetInfoByAssetTypeIdAndPagination(Integer assetTypeId, int page, int size) {
		log.info("MasterAssetServiceImpl:getAllAssetInfoByAssetTypeIdAndPagination info level log message");
		try {
			if (assetTypeId == 0 || assetTypeId.equals("")) {
				throw new IllegalArgumentException(
						"Provide valid Asset type id, it should not be 0 or invalid or null");
			}
			if (size <= 0 || size > MAX_PAGE_SIZE) {
				size = DEFAULT_PAGE_SIZE;
			}

			Pageable pageable = PageRequest.of(page, size);
			Page<AssetInfo> assetInfoListPage = assetInfoRepo.findListByAssetTypeIdWithPagination(assetTypeId,
					pageable);

			if (!assetInfoListPage.isEmpty()) {
				List<AssetDTO> assetDTOList = new ArrayList<>();

				List<AssetAttribute> allAttributesForType = assetAttributeRepo
						.findAllAssetAttributesByAssetTypeId(assetTypeId);

				for (AssetInfo assetInfo : assetInfoListPage) {
					AssetDTO assetDTO = new AssetDTO();

					Optional<AssetType> existingAssetType = assetTypeRepo
							.findAssetByAssetTypeId(assetInfo.getAsset_type_id());

					Optional<AssetStatus> assetStatusExist = assetStatusRepo.findById(assetInfo.getAsset_status_id());

					assetDTO.setAssetAdtId(assetInfo.getAssetADT_ID());
					assetDTO.setAssetStatus(
							assetStatusExist.isPresent() ? assetStatusExist.get().getAssetStatus() : "Unknown");
					assetDTO.setAssetTypeName(
							existingAssetType.isPresent() ? existingAssetType.get().getAssetName() : "Unknown");

					assetDTO.setEmpId(assetInfo.getEmp_id());

					List<AssetAttributeMappingDTO> attributeMappingDTOList = new ArrayList<>();

					List<AssetAttributeMapping> assetAttributeMappingsList = assetAttributeMappingRepo
							.findAssetMappingListByAssetId(assetInfo.getId());

					Map<String, String> existingAttributes = new HashMap<>();
					for (AssetAttributeMapping assetAttributeMapping : assetAttributeMappingsList) {
						Optional<AssetAttribute> attributeExist = assetAttributeRepo
								.findById(assetAttributeMapping.getAsset_attribute_id());
						if (attributeExist.isPresent()) {
							existingAttributes.put(attributeExist.get().getAssetAttributeName(),
									assetAttributeMapping.getAssetAttributeValue());
						}
					}

					for (AssetAttribute attribute : allAttributesForType) {
						AssetAttributeMappingDTO attributeMappingDTO = new AssetAttributeMappingDTO();
						attributeMappingDTO.setAssetAttributeName(attribute.getAssetAttributeName());

						attributeMappingDTO.setAssetAttributeValue(
								existingAttributes.getOrDefault(attribute.getAssetAttributeName(), "null"));

						attributeMappingDTOList.add(attributeMappingDTO);
					}

					assetDTO.setAssetAttributeMappingList(attributeMappingDTOList);
					assetDTOList.add(assetDTO);
				}
				return buildResponse("Success", "Asset info list retrieved successfully ", assetDTOList);

			} else {
				return buildResponse("NotFound", "Asset info list not found ", null);
			}
		} catch (IllegalArgumentException e) {
			log.error("getAllAssetInfoByAssetTypeIdAndPagination IllegalArgumentException : {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("MasterAssetServiceImpl: getAllAssetInfoByAssetTypeIdAndPagination Exception : {} ",
					e.getMessage());
			return buildResponse("Failed", "Internal server error occurred", null);
		}
	}

	@Override
	public ResponseDTO updateAssetAttributeMappingByAssetAdtId(AssetDTO assetDTO) {
		log.info("MasterAssetServiceImpl:updateAssetAttributeMappingByAssetId info level log message");

		try {
			if (assetDTO.getAssetAdtId() == null || assetDTO.getAssetAdtId().isEmpty()
					|| assetDTO.getAssetAdtId().equals("") || assetDTO.getAssetAdtId().isBlank()) {
				throw new IllegalArgumentException("Provide valid Asset adt id, it should not be 0 or invalid or null");
			}
			if (assetDTO.getAssetAttributeMappingList() == null || assetDTO.getAssetAttributeMappingList().isEmpty()) {
				throw new IllegalArgumentException(
						"Provide valid Asset attribute mapping list, it should not be null or invalid");
			}
			if (assetDTO.getAssetStatus().toUpperCase().equalsIgnoreCase("ALLOTTED")) {
				throw new IllegalArgumentException("Asset status cannot be marked as allotted");
			}
			for (AssetAttributeMappingDTO assetAttributeMapping : assetDTO.getAssetAttributeMappingList()) {
				if (assetAttributeMapping.getAssetAttributeName() == null
						|| assetAttributeMapping.getAssetAttributeName().isBlank()
						|| assetAttributeMapping.getAssetAttributeName().equals("")
						|| assetAttributeMapping.getAssetAttributeName().length() > 50) {
					throw new IllegalArgumentException(
							"Asset attribute name should not be null, blank, or greater than 50 characters");
				}
				if (assetAttributeMapping.getAssetAttributeValue() == null
						|| assetAttributeMapping.getAssetAttributeValue().isBlank()
						|| assetAttributeMapping.getAssetAttributeValue().length() > 50) {
					throw new IllegalArgumentException(
							"Asset attribute value should not be null, blank, or greater than 50 characters");
				}
			}
			// Fetch the existing AssetInfo by AssetAdtId
			Optional<AssetInfo> assetInfoExist = assetInfoRepo.findAssetsByAdtId(assetDTO.getAssetAdtId());

			if (assetInfoExist.isPresent()) {

				Optional<AssetStatus> assetStatusExist = assetStatusRepo
						.findAssetStatus(assetDTO.getAssetStatus().toUpperCase());
				if (assetStatusExist.isEmpty()) {
					assetInfoExist.get().setAsset_status_id(null);
				} else {
					assetInfoExist.get().setAsset_status_id(assetStatusExist.get().getId());
				}

				AssetInfo assetInfoSaved = assetInfoRepo.save(assetInfoExist.get());
				if (assetInfoSaved != null) {
					// Fetch existing mappings
					Optional<List<AssetAttributeMapping>> assetAttributeMappingListExist = assetAttributeMappingRepo
							.findMappingListByAssetId(assetInfoExist.get().getId());

					Map<String, AssetAttributeMapping> existingMappings = new HashMap<>();
					if (assetAttributeMappingListExist.isPresent()) {
						for (AssetAttributeMapping mapping : assetAttributeMappingListExist.get()) {
							existingMappings.put(mapping.getAssetAttribute().getAssetAttributeName().toUpperCase(),
									mapping);
						}
					}

					// Process input mappings from the UI
					for (AssetAttributeMappingDTO assetMappingDTO : assetDTO.getAssetAttributeMappingList()) {
						String attributeName = assetMappingDTO.getAssetAttributeName().toUpperCase();
						String attributeValue = assetMappingDTO.getAssetAttributeValue().toUpperCase();

						if (existingMappings.containsKey(attributeName)) {
							// Update existing mapping
							AssetAttributeMapping existingMapping = existingMappings.get(attributeName);
							existingMapping.setAssetAttributeValue(attributeValue);
							assetAttributeMappingRepo.save(existingMapping);
							existingMappings.remove(attributeName); // Remove processed mapping from map
						} else {
							// Create new mapping
							AssetAttributeMapping newMapping = new AssetAttributeMapping();
							newMapping.setAsset_id(assetInfoExist.get().getId());

							// Check if the attribute already exists
							Optional<AssetAttribute> attributeExist = assetAttributeRepo
									.findAttributeByName(attributeName, assetInfoExist.get().getAsset_type_id());

							if (attributeExist.isPresent()) {
								newMapping.setAsset_attribute_id(attributeExist.get().getId());
							} else {
								// Create a new attribute if it doesn't exist
								AssetAttribute newAttribute = new AssetAttribute();
								newAttribute.setAssetAttributeName(attributeName.toUpperCase());
								newAttribute.setAsset_type_id(assetInfoExist.get().getAsset_type_id());
								AssetAttribute savedAttribute = assetAttributeRepo.save(newAttribute);
								newMapping.setAsset_attribute_id(savedAttribute.getId());
							}
							newMapping.setAssetAttributeValue(attributeValue);
							assetAttributeMappingRepo.save(newMapping);
						}
					}

					// Set remaining attributes to null
					for (Map.Entry<String, AssetAttributeMapping> entry : existingMappings.entrySet()) {
						AssetAttributeMapping mapping = entry.getValue();
						mapping.setAssetAttributeValue(null);
						assetAttributeMappingRepo.save(mapping);
					}

					return buildResponse("Success", "Asset attribute mapping is updated successfully", null);
				}
				return buildResponse("NotUpdated", "Asset info not updated yet", null);
			} else {
				return buildResponse("NotFound", "Asset info not found", null);
			}
		} catch (IllegalArgumentException e) {
			log.error("updateAssetAttributeMappingByAssetId IllegalArgumentException: {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("MasterAssetServiceImpl: updateAssetAttributeMappingByAssetId Exception: {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occurred", null);
		}
	}

	@Override
	public ResponseDTO deleteAssetInfoByAssetAdtId(String assetAdtId) {
		log.info("MasterAssetServiceImpl:deleteAssetInfoByAssetAdtId info level log message");
		try {
			if (assetAdtId == null || assetAdtId.equals("") || assetAdtId.isBlank() || assetAdtId.isEmpty()) {
				throw new IllegalArgumentException("Provide valid Asset adt id, it should not be 0 or invalid or null");
			}

			Optional<AssetInfo> assetInfoExist = assetInfoRepo.findAssetsByAdtId(assetAdtId);

			if (assetInfoExist.isPresent()) {

				Optional<List<AssetAttributeMapping>> assetAttributeMappingListExist = assetAttributeMappingRepo
						.findMappingListByAssetId(assetInfoExist.get().getId());

				if (assetAttributeMappingListExist.isPresent()) {

					for (AssetAttributeMapping assetAttributeMapping : assetAttributeMappingListExist.get()) {

						assetAttributeMappingRepo.deleteById(assetAttributeMapping.getId());
					}
				}

				assetInfoRepo.deleteById(assetInfoExist.get().getId());
				return buildResponse("Success", "Asset info deleted successfully", null);
			} else {
				return buildResponse("NotFound", "Asset info not found", null);
			}
		} catch (IllegalArgumentException e) {
			log.error("deleteAssetInfoById IllegalArgumentException : {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("MasterAssetServiceImpl: deleteAssetInfoById Exception : {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occured", null);
		}
	}

	@Override
	public ResponseDTO getAssetInfoByAssetAdtId(String assetAdtId) {
		log.info("MasterAssetServiceImpl:getAssetInfoByAssetAdtId info level log message");

		if (assetAdtId == null || assetAdtId.isEmpty() || assetAdtId.isBlank()) {
			throw new IllegalArgumentException("Provide valid Asset adt id, it should not be null or blank");
		}

		try {
			AssetInfo assetInfo = assetInfoRepo.findAssetsByAdtId(assetAdtId)
					.orElseThrow(() -> new IllegalArgumentException("Asset adt id:" + assetAdtId + " not found"));

			AssetType assetType = assetTypeRepo.findById(assetInfo.getAsset_type_id())
					.orElseThrow(() -> new IllegalArgumentException("Asset type not found"));

			Optional<AssetStatus> assetStatusExist = assetStatusRepo.findById(assetInfo.getAsset_status_id());

			// ALLOTTED -1
			if ("ALLOTTED".equalsIgnoreCase(assetStatusExist.get().getAssetStatus())) {
				Employee emp = employeeRepo.findEmployeeById(assetInfo.getEmp_id())
						.orElseThrow(() -> new IllegalArgumentException("Employee not found"));
				String empName = emp.getFirstName() + " " + emp.getLastName();
				return buildResponse("AlreadyExist", "Asset adt id:" + assetAdtId + " is already assigned to Employee:"
						+ empName + ", please select another asset", null);
			}
			// DEFECTIVE-3
			if ("DEFECTIVE".equalsIgnoreCase(assetStatusExist.get().getAssetStatus())) {
				return buildResponse("Success", "Asset adt id:" + assetAdtId + " is defective", null);
			}
			// DISCARDED-4
			if ("DISCARDED".equalsIgnoreCase(assetStatusExist.get().getAssetStatus())) {
				return buildResponse("Success", "Asset adt id:" + assetAdtId + " is discarded", null);
			}

			List<AssetAttributeMappingDTO> assetAttributeMappingDTOList = new ArrayList<>();
			List<AssetAttributeMapping> assetAttributeMappingsList = assetAttributeMappingRepo
					.findAssetMappingListByAssetId(assetInfo.getId());

			// AVAILABLE-2
			for (AssetAttributeMapping assetAttributeMapping : assetAttributeMappingsList) {
				AssetAttributeMappingDTO assetAttributeMappingDTO = new AssetAttributeMappingDTO();
				AssetAttribute assetAttribute = assetAttributeRepo
						.findById(assetAttributeMapping.getAsset_attribute_id()).orElse(null);
				if (assetAttribute != null) {
					assetAttributeMappingDTO.setAssetAttributeName(assetAttribute.getAssetAttributeName());
					assetAttributeMappingDTO.setAssetAttributeValue(assetAttributeMapping.getAssetAttributeValue());
				}
				assetAttributeMappingDTOList.add(assetAttributeMappingDTO);
			}

			AssetInfoDTO assetDTO = new AssetInfoDTO();
			if (assetInfo.getEmp_id() == null) {
				assetDTO.setEmpADTId(null);
			}
			assetDTO.setAssetADTId(assetInfo.getAssetADT_ID());
			assetDTO.setAssetType(assetType.getAssetName());
			assetDTO.setAssetStatus(assetStatusExist.get().getAssetStatus());
			assetDTO.setAssetAttributeMappingList(assetAttributeMappingDTOList);

			return buildResponse("Success", "This asset is available to assign", assetDTO);

		} catch (IllegalArgumentException e) {
			log.error("getAssetInfoByAssetAdtId IllegalArgumentException: ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("MasterAssetServiceImpl: getAssetInfoByAssetAdtId Exception: {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occurred", null);
		}
	}

	@Override
	public ResponseDTO getAllAssignedAssetsByEmpADTId(String empADTId) {
		log.info("MasterAssetServiceImpl:getAllAssignedAssetsByEmpADTId info level log message");
		try {
			if (empADTId == null || empADTId.isEmpty() || empADTId.isBlank()) {
				throw new IllegalArgumentException("Provide a valid Employee adt id, it should not be null or blank");
			}

			Optional<Employee> employeeExist = employeeRepo.findEmpByAdtId(empADTId);
			if (employeeExist.isEmpty()) {
				return buildResponse("NotFound", "Employee not found", null);
			}

			List<AssetInfo> assetInfoList = assetInfoRepo.findAssetsByEmpId(employeeExist.get().getEmployeeId());
			List<AssetInfoDTO> assetInfoDTOList = new ArrayList<>();

			if (assetInfoList.isEmpty()) {
				return buildResponse("NotFound", "Assets not assigned yet", assetInfoDTOList);
			}

			for (AssetInfo assetInfo : assetInfoList) {
				Optional<AssetStatus> assetStatusExist = assetStatusRepo.findById(assetInfo.getAsset_status_id());

				AssetInfoDTO assetInfoDTO = new AssetInfoDTO();
				assetInfoDTO.setEmpADTId(employeeExist.get().getAdtId());
				assetInfoDTO.setAssetADTId(assetInfo.getAssetADT_ID());
				assetInfoDTO.setAssetStatus(assetStatusExist.get().getAssetStatus());

				Optional<AssetType> assetTypeExist = assetTypeRepo.findById(assetInfo.getAsset_type_id());
				assetTypeExist.ifPresent(assetType -> assetInfoDTO.setAssetType(assetType.getAssetName()));

				List<AssetAttributeMappingDTO> assetAttributeMappingDTOList = new ArrayList<>();
				List<AssetAttributeMapping> attributeMappingList = assetAttributeMappingRepo
						.findAssetMappingListByAssetId(assetInfo.getId());

				for (AssetAttributeMapping attributeMapping : attributeMappingList) {
					AssetAttributeMappingDTO assetAttributeMappingDTO = new AssetAttributeMappingDTO();
					assetAttributeMappingDTO
							.setAssetAttributeName(attributeMapping.getAssetAttribute().getAssetAttributeName());
					assetAttributeMappingDTO.setAssetAttributeValue(attributeMapping.getAssetAttributeValue());
					assetAttributeMappingDTOList.add(assetAttributeMappingDTO);
				}

				assetInfoDTO.setAssetAttributeMappingList(assetAttributeMappingDTOList);
				assetInfoDTOList.add(assetInfoDTO);
			}
			return buildResponse("Success", "All assigned assets fetched successfully", assetInfoDTOList);

		} catch (IllegalArgumentException e) {
			log.error("getAllAssignedAssetsByEmpADTId IllegalArgumentException: {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("MasterAssetServiceImpl: getAllAssignedAssetsByEmpADTId Exception: {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occurred", null);
		}
	}

	@Override
	public ResponseDTO searchEmployeeDetails(String firstName, String lastName, String empAdtId, String firstLetter,
			int page, int size) {
		log.info("MasterAssetServiceImpl:searchEmployeeDetails info level log message");
		try {
			if (firstName != null && !firstName.isEmpty() && containsDigit(firstName)) {
				throw new IllegalArgumentException("First name cannot contain digits or numbers");
			} else if (firstName == "") {
				throw new IllegalArgumentException("First name cannot be empty");
			}
			if (lastName != null && !lastName.isEmpty() && containsDigit(lastName)) {
				throw new IllegalArgumentException("Last name cannot contain digits or numbers");
			} else if (lastName == "") {
				throw new IllegalArgumentException("Last name cannot be empty");
			}
			if ((empAdtId != null && !empAdtId.isEmpty()) && !containsDigit(empAdtId)) {
				throw new IllegalArgumentException("Provide valid Employee adt id");
			} else if (empAdtId == "") {
				throw new IllegalArgumentException("Employee adt id cannot be empty or null");
			}

			Pageable pageable = PageRequest.of(page, size);
			Specification<Employee> spec = Specification.where(null);

			if (firstName != null && !firstName.isEmpty()) {
				spec = spec.or(
						(root, query, cb) -> cb.like(cb.lower(root.get("firstName")), firstName.toLowerCase() + "%"));
			}
			if (lastName != null && !lastName.isEmpty()) {
				spec = spec
						.or((root, query, cb) -> cb.like(cb.lower(root.get("lastName")), lastName.toLowerCase() + "%"));
			}
			if (empAdtId != null && !empAdtId.isEmpty()) {
				spec = spec.or((root, query, cb) -> cb.like(cb.lower(root.get("adtId")), empAdtId.toLowerCase() + "%")); // Changed
			}

			Page<Employee> emp = employeeRepo.findAll(spec, pageable);
			if (emp.isEmpty() || emp == null) {
				return buildResponse("NotFound", "Employee not found", null);
			}

			List<EmployeeDTO> empDTOList = new ArrayList<>();
			for (Employee employee : emp) {
				EmployeeDTO empDTO = new EmployeeDTO();

				empDTO.setFirstName(employee.getFirstName());
				empDTO.setLastName(employee.getLastName());
				empDTO.setEmpAdtId(employee.getAdtId());
				empDTOList.add(empDTO);
			}
			return buildResponse("Success", "Employee fetched successfully", empDTOList);

		} catch (IllegalArgumentException e) {
			log.error("searchEmployeeDetails IllegalArgumentException : {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("MasterAssetServiceImpl: searchEmployeeDetails Exception: {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occurred", null);
		}
	}

	@Override
	public ResponseDTO assignAllAssetsToEmp(AssignAssetsDTO assignAssetsDTO) {
		log.info("MasterAssetServiceImpl:assignAllAssetsToEmp info level log message");
		try {
			if (assignAssetsDTO.getEmpAdtId() == null || assignAssetsDTO.getEmpAdtId().trim().isEmpty()) {
				throw new IllegalArgumentException("Provide valid Employee adt id, it should not be null or blank");
			}
			if (assignAssetsDTO.getAssetAdtIdList() == null || assignAssetsDTO.getAssetAdtIdList().isEmpty()) {
				throw new IllegalArgumentException("Provide valid Asset adt id list, it should not be null or empty");
			}

			Optional<Employee> employeeOpt = employeeRepo.findEmpByAdtId(assignAssetsDTO.getEmpAdtId());
			if (employeeOpt.isEmpty()) {
				return buildResponse("NotFound", "Employee not found", null);
			}

			Employee employee = employeeOpt.get();
			List<AssetInfo> assetInfoList = new ArrayList<>();

			for (String assetAdtId : assignAssetsDTO.getAssetAdtIdList()) {
				Optional<AssetInfo> assetOpt = assetInfoRepo.findAssetsByAdtId(assetAdtId);
				if (assetOpt.isEmpty()) {
					return buildResponse("NotFound", "Asset adt id: " + assetAdtId + " not found", null);
				}
				assetInfoList.add(assetOpt.get());
			}

			AssetInfo updatedAsset = new AssetInfo();
			for (AssetInfo asset : assetInfoList) {
				asset.setEmp_id(employee.getEmployeeId());
				asset.setAsset_status_id(1);
				updatedAsset = assetInfoRepo.save(asset);
			}
			if (updatedAsset == null || updatedAsset.equals("")) {
				return buildResponse("NotSaved", "All assets not assigned", null);
			}
			return buildResponse("Success", "All assets assigned successfully", null);

		} catch (IllegalArgumentException e) {
			log.error("assignAllAssetsToEmp IllegalArgumentException: {} ", e.getMessage());
			return buildResponse("Failed", e.getMessage(), null);
		} catch (Exception e) {
			log.error("MasterAssetServiceImpl: assignAllAssetsToEmp Exception: {} ", e.getMessage());
			return buildResponse("Failed", "Internal server error occurred", null);
		}
	}

}
